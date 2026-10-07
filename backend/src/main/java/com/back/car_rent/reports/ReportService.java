package com.back.car_rent.reports;

import com.back.car_rent.config.ApiException;
import com.back.car_rent.model.Car;
import com.back.car_rent.model.Contract;
import com.back.car_rent.model.Expense;
import com.back.car_rent.payments.Payment;
import com.back.car_rent.payments.PaymentRepository;
import com.back.car_rent.repository.CarRepository;
import com.back.car_rent.repository.ContractRepository;
import com.back.car_rent.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Reports section, over whole months [from, to]:
 * <ul>
 *   <li><b>billed</b>: the total value of the contracts that start in the month (canceled ones excluded);</li>
 *   <li><b>collected</b>: the payments dated in the month;</li>
 *   <li><b>expenses</b>: the expenses dated in the month; <b>net</b> = collected - expenses;</li>
 *   <li><b>fleet utilization</b>: nights each car spends in a reserved, active or completed contract inside the
 *       period, out of the nights in the period;</li>
 *   <li><b>outstanding</b>: what is still owed on every contract that is not canceled (not only this period's),
 *       with the biggest debts listed first.</li>
 * </ul>
 */
@Service
public class ReportService {

    static final int MAX_MONTHS = 24;
    private static final Set<String> USES_THE_CAR = Set.of("Reserved", "Active", "Completed");

    private final ContractRepository contracts;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final CarRepository cars;
    private final String currency;

    public ReportService(ContractRepository contracts, PaymentRepository payments, ExpenseRepository expenses,
                         CarRepository cars, @Value("${app.agency.currency:TND}") String currency) {
        this.contracts = contracts;
        this.payments = payments;
        this.expenses = expenses;
        this.cars = cars;
        this.currency = currency;
    }

    @Transactional(readOnly = true)
    public Report summary(String fromText, String toText) {
        YearMonth to = toText == null || toText.isBlank() ? YearMonth.now() : month(toText);
        YearMonth from = fromText == null || fromText.isBlank() ? to.minusMonths(5) : month(fromText);
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must not be after 'to'");
        }
        if (ChronoUnit.MONTHS.between(from, to) >= MAX_MONTHS) {
            throw ApiException.badRequest("A report covers at most " + MAX_MONTHS + " months");
        }
        LocalDate start = from.atDay(1);
        LocalDate end = to.plusMonths(1).atDay(1); // exclusive

        List<Contract> allContracts = contracts.findAll();
        List<Payment> allPayments = payments.findAll();

        Map<YearMonth, double[]> byMonth = new LinkedHashMap<>(); // billed, collected, expenses
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            byMonth.put(m, new double[3]);
        }
        Map<String, double[]> byCategory = new LinkedHashMap<>(); // rentals, billed
        Map<String, Double> billedByPlate = new HashMap<>();
        Map<String, Long> nightsByPlate = new HashMap<>();
        Map<String, String> categoryByPlate = new HashMap<>();
        List<Car> fleet = cars.findAll();
        fleet.forEach(c -> categoryByPlate.put(c.getLicensePlate(), c.getCategory() == null ? "Other" : c.getCategory()));

        for (Contract c : allContracts) {
            if ("Canceled".equals(c.getStatus())) {
                continue;
            }
            LocalDate cStart = parse(c.getStartDate());
            LocalDate cEnd = parse(c.getEndDate());
            double total = c.getTotalValue() == null ? 0 : c.getTotalValue();
            if (cStart != null && !cStart.isBefore(start) && cStart.isBefore(end)) {
                byMonth.get(YearMonth.from(cStart))[0] += total;
                billedByPlate.merge(c.getLicensePlate(), total, Double::sum);
                double[] cat = byCategory.computeIfAbsent(categoryByPlate.getOrDefault(c.getLicensePlate(), "Other"), k -> new double[2]);
                cat[0] += 1;
                cat[1] += total;
            }
            if (cStart != null && cEnd != null && USES_THE_CAR.contains(c.getStatus())) {
                LocalDate a = cStart.isAfter(start) ? cStart : start;
                LocalDate b = cEnd.isBefore(end) ? cEnd : end;
                if (b.isAfter(a)) {
                    nightsByPlate.merge(c.getLicensePlate(), ChronoUnit.DAYS.between(a, b), Long::sum);
                }
            }
        }
        for (Payment p : allPayments) {
            LocalDate d = parse(p.getDate());
            if (d != null && !d.isBefore(start) && d.isBefore(end)) {
                byMonth.get(YearMonth.from(d))[1] += amount(p.getAmount());
            }
        }
        for (Expense e : expenses.findAll()) {
            LocalDate d = parse(e.getDate());
            if (d != null && !d.isBefore(start) && d.isBefore(end)) {
                byMonth.get(YearMonth.from(d))[2] += amount(e.getAmount());
            }
        }

        List<Report.Month> months = new ArrayList<>();
        double billed = 0, collected = 0, spent = 0;
        for (Map.Entry<YearMonth, double[]> m : byMonth.entrySet()) {
            double[] v = m.getValue();
            months.add(new Report.Month(m.getKey().toString(), round(v[0]), round(v[1]), round(v[2]), round(v[1] - v[2])));
            billed += v[0];
            collected += v[1];
            spent += v[2];
        }

        long nightsInPeriod = ChronoUnit.DAYS.between(start, end);
        List<Report.CarUse> carUse = new ArrayList<>();
        long rentedNights = 0;
        for (Car car : fleet) {
            long nights = nightsByPlate.getOrDefault(car.getLicensePlate(), 0L);
            rentedNights += nights;
            carUse.add(new Report.CarUse(car.getLicensePlate(), car.getMake() + " " + car.getModel(),
                    categoryByPlate.get(car.getLicensePlate()), nights, nightsInPeriod,
                    percent(nights, nightsInPeriod), round(billedByPlate.getOrDefault(car.getLicensePlate(), 0.0))));
        }
        carUse.sort(Comparator.comparingDouble(Report.CarUse::utilizationPercent).reversed());

        List<Report.Category> categories = new ArrayList<>();
        byCategory.forEach((name, v) -> categories.add(new Report.Category(name, (int) v[0], round(v[1]))));
        categories.sort(Comparator.comparingDouble(Report.Category::billed).reversed());

        Map<Long, Double> paidByContract = new HashMap<>();
        allPayments.forEach(p -> paidByContract.merge(p.getContractRef(), amount(p.getAmount()), Double::sum));
        List<Report.Debtor> debtors = new ArrayList<>();
        double outstanding = 0;
        for (Contract c : allContracts) {
            if ("Canceled".equals(c.getStatus())) {
                continue;
            }
            double total = c.getTotalValue() == null ? 0 : c.getTotalValue();
            double paid = paidByContract.getOrDefault(c.getId(), 0.0);
            double left = total - paid;
            if (left > 0.005) {
                outstanding += left;
                debtors.add(new Report.Debtor(c.getId(), c.getContractId(), c.getClientName(), c.getEndDate(),
                        round(total), round(paid), round(left)));
            }
        }
        debtors.sort(Comparator.comparingDouble(Report.Debtor::left).reversed());

        Report.Totals totals = new Report.Totals(round(billed), round(collected), round(spent), round(collected - spent),
                round(outstanding), percent(rentedNights, nightsInPeriod * Math.max(1, fleet.size())));
        return new Report(from.toString(), to.toString(), currency, totals, months, carUse, categories, debtors);
    }

    private static YearMonth month(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Months look like 2026-03");
        }
    }

    private static LocalDate parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static double amount(Double value) {
        return value == null ? 0 : value;
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
