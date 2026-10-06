package com.back.car_rent.pricing;

import com.back.car_rent.config.ApiException;
import com.back.car_rent.model.Car;
import com.back.car_rent.repository.CarRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Prices a rental from the car's daily rate and the agency's rules. Nights are counted like the contract does
 * (end minus start). When several season rules cover a night, the one that moves the price furthest from the
 * normal rate wins; the best long-stay discount applies to the total.
 */
@Service
public class PricingService {

    private final CarRepository cars;
    private final PricingRuleRepository rules;

    public PricingService(CarRepository cars, PricingRuleRepository rules) {
        this.cars = cars;
        this.rules = rules;
    }

    public Quote quote(String plate, String from, String to) {
        LocalDate start = date(from, "from");
        LocalDate end = date(to, "to");
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("The end date must be after the start date");
        }
        Car car = cars.findFirstByLicensePlate(plate).orElseThrow(() -> ApiException.badRequest("Unknown car " + plate));
        double base = car.getDailyRate() == null ? 0 : car.getDailyRate();
        List<PricingRule> all = rules.findAll();
        List<PricingRule> seasons = all.stream().filter(r -> "SEASON".equals(r.getType()) && applies(r, car)).toList();

        int days = (int) ChronoUnit.DAYS.between(start, end);
        List<Quote.Line> lines = new ArrayList<>();
        PricingRule current = null;
        LocalDate runStart = start;
        int run = 0;
        for (int i = 0; i < days; i++) {
            LocalDate night = start.plusDays(i);
            PricingRule rule = seasonOn(seasons, night).orElse(null);
            if (i > 0 && rule != current) {
                lines.add(line(current, runStart, run, base));
                runStart = night;
                run = 0;
            }
            current = rule;
            run++;
        }
        lines.add(line(current, runStart, run, base));

        double subtotal = round(lines.stream().mapToDouble(Quote.Line::amount).sum());
        Optional<PricingRule> stay = all.stream()
                .filter(r -> "LONG_STAY".equals(r.getType()) && applies(r, car) && r.getMinDays() != null && days >= r.getMinDays())
                .max(Comparator.comparingDouble(r -> r.getDiscountPercent() == null ? 0 : r.getDiscountPercent()));
        double percent = stay.map(r -> r.getDiscountPercent() == null ? 0 : r.getDiscountPercent()).orElse(0.0);
        double discount = round(subtotal * percent / 100);
        double total = round(subtotal - discount);
        String label = stay.filter(r -> percent > 0).map(r -> r.getName() + " (-" + trim(percent) + "%)").orElse(null);
        return new Quote(plate, days, base, lines, subtotal, label, percent, discount, total, round(total / days));
    }

    /** Checks run when an administrator saves a rule. */
    public void validate(PricingRule rule) {
        if (rule.getName() == null || rule.getName().isBlank()) {
            throw ApiException.badRequest("A rule needs a name");
        }
        if ("SEASON".equals(rule.getType())) {
            LocalDate start = date(rule.getStartDate(), "startDate");
            LocalDate end = date(rule.getEndDate(), "endDate");
            if (end.isBefore(start)) {
                throw ApiException.badRequest("The season must end on or after its start");
            }
            if (rule.getMultiplier() == null || rule.getMultiplier() <= 0 || rule.getMultiplier() > 10) {
                throw ApiException.badRequest("The multiplier must be between 0 and 10 (1.3 means +30%)");
            }
        } else if ("LONG_STAY".equals(rule.getType())) {
            if (rule.getMinDays() == null || rule.getMinDays() < 2) {
                throw ApiException.badRequest("minDays must be 2 or more");
            }
            if (rule.getDiscountPercent() == null || rule.getDiscountPercent() <= 0 || rule.getDiscountPercent() >= 100) {
                throw ApiException.badRequest("discountPercent must be between 0 and 100");
            }
        } else {
            throw ApiException.badRequest("type must be SEASON or LONG_STAY");
        }
    }

    private Optional<PricingRule> seasonOn(List<PricingRule> seasons, LocalDate night) {
        String day = night.toString();
        return seasons.stream()
                .filter(r -> r.getStartDate().compareTo(day) <= 0 && r.getEndDate().compareTo(day) >= 0)
                .max(Comparator.comparingDouble(r -> Math.abs(r.getMultiplier() - 1)));
    }

    private static boolean applies(PricingRule rule, Car car) {
        return rule.getCategory() == null || rule.getCategory().isBlank()
                || rule.getCategory().equalsIgnoreCase(car.getCategory());
    }

    private static Quote.Line line(PricingRule rule, LocalDate from, int nights, double base) {
        double rate = round(rule == null ? base : base * rule.getMultiplier());
        String label = rule == null ? "Standard rate" : rule.getName() + " (x" + trim(rule.getMultiplier()) + ")";
        return new Quote.Line(label, from.toString(), from.plusDays(nights).toString(), nights, rate, round(rate * nights));
    }

    private static LocalDate date(String value, String name) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.badRequest(name + " must look like 2026-11-02");
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
