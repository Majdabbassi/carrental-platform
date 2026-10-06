package com.back.car_rent.alerts;

import com.back.car_rent.model.Car;
import com.back.car_rent.model.Contract;
import com.back.car_rent.repository.CarRepository;
import com.back.car_rent.repository.ContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks at the fleet and the contracts every morning (and once at start-up) and keeps one alert per problem:
 * insurance or registration expiring within 30 days, a service due within 14 days, rentals that should have been
 * returned, and cars due back today. An alert disappears by itself once its cause is gone; an alert somebody
 * dismissed stays dismissed while the cause remains.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    static final int DOCUMENT_WARNING_DAYS = 30;
    static final int SERVICE_WARNING_DAYS = 14;

    private final AlertRepository alerts;
    private final CarRepository cars;
    private final ContractRepository contracts;

    public AlertService(AlertRepository alerts, CarRepository cars, ContractRepository contracts) {
        this.alerts = alerts;
        this.cars = cars;
        this.contracts = contracts;
    }

    @Scheduled(cron = "${app.alerts.cron:0 0 6 * * *}")
    public void scheduledRefresh() {
        refresh();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshAtStartup() {
        refresh();
    }

    @Transactional
    public int refresh() {
        LocalDate today = LocalDate.now();
        Map<String, Alert> found = new LinkedHashMap<>();
        for (Car car : cars.findAll()) {
            String name = car.getMake() + " " + car.getModel() + " (" + car.getLicensePlate() + ")";
            expiry(found, today, "INSURANCE", "insurance", car.getInsuranceExpiry(), DOCUMENT_WARNING_DAYS, car, name, "Insurance");
            expiry(found, today, "REGISTRATION", "registration", car.getRegistrationExpiry(), DOCUMENT_WARNING_DAYS, car, name, "Registration");
            expiry(found, today, "MAINTENANCE", "service", car.getNextMaintenance(), SERVICE_WARNING_DAYS, car, name, "Service");
        }
        for (Contract contract : contracts.findAll()) {
            if (!"Active".equals(contract.getStatus())) {
                continue;
            }
            LocalDate end = parse(contract.getEndDate());
            if (end == null) {
                continue;
            }
            String who = contract.getClientName() + " with " + contract.getCarMake() + " " + contract.getCarModel()
                    + " (" + contract.getLicensePlate() + ")";
            if (end.isBefore(today)) {
                long late = ChronoUnit.DAYS.between(end, today);
                add(found, "overdue:" + contract.getContractId(), "OVERDUE_RETURN", "CRITICAL",
                        "Car not returned: " + contract.getContractId(),
                        who + " was due back on " + end + " (" + late + (late == 1 ? " day" : " days") + " late).", end.toString());
            } else if (end.isEqual(today)) {
                add(found, "due:" + contract.getContractId(), "RETURN_DUE", "INFO",
                        "Car due back today: " + contract.getContractId(), who + " is due back today.", end.toString());
            }
        }

        List<Alert> existing = alerts.findAll();
        Set<String> keys = found.keySet();
        List<Alert> gone = existing.stream().filter(a -> !keys.contains(a.getRefKey())).toList();
        alerts.deleteAll(gone);
        Set<String> known = existing.stream().map(Alert::getRefKey).collect(java.util.stream.Collectors.toSet());
        List<Alert> fresh = found.values().stream().filter(a -> !known.contains(a.getRefKey())).toList();
        alerts.saveAll(fresh);
        log.info("Alerts refreshed: {} open, {} new, {} resolved", found.size(), fresh.size(), gone.size());
        return fresh.size();
    }

    /** Open alerts, most urgent first. */
    @Transactional(readOnly = true)
    public List<Alert> open() {
        List<Alert> open = new ArrayList<>(alerts.findByDismissedFalse());
        open.sort(Comparator.comparingInt((Alert a) -> rank(a.getSeverity())).thenComparing(a -> a.getDueDate() == null ? "" : a.getDueDate()));
        return open;
    }

    @Transactional
    public boolean dismiss(Long id) {
        return alerts.findById(id).map(a -> {
            a.setDismissed(true);
            alerts.save(a);
            return true;
        }).orElse(false);
    }

    private void expiry(Map<String, Alert> found, LocalDate today, String type, String key, String value, int warningDays,
                        Car car, String carName, String noun) {
        LocalDate due = parse(value);
        if (due == null) {
            return;
        }
        long left = ChronoUnit.DAYS.between(today, due);
        if (left > warningDays) {
            return;
        }
        String severity = left < 0 || left <= 7 ? "CRITICAL" : "WARNING";
        String when = left < 0 ? "expired " + (-left) + (left == -1 ? " day ago" : " days ago")
                : left == 0 ? "expires today" : "expires in " + left + (left == 1 ? " day" : " days");
        if (type.equals("MAINTENANCE")) {
            when = left < 0 ? "was due " + (-left) + (left == -1 ? " day ago" : " days ago")
                    : left == 0 ? "is due today" : "is due in " + left + (left == 1 ? " day" : " days");
        }
        add(found, key + ":" + car.getLicensePlate() + ":" + due, type, severity,
                noun + " " + (left < 0 ? "overdue" : "due soon") + ": " + carName,
                noun + " for " + carName + " " + when + " (" + due + ").", due.toString());
    }

    private void add(Map<String, Alert> found, String key, String type, String severity, String title, String message, String due) {
        found.put(key, Alert.builder().refKey(key).type(type).severity(severity).title(title).message(message)
                .dueDate(due).createdAt(LocalDate.now().toString()).build());
    }

    private static LocalDate parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int rank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }
}
