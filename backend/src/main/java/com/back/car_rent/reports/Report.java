package com.back.car_rent.reports;

import java.util.List;

/** The agency's numbers over a range of whole months (see ReportService for how each one is counted). */
public record Report(String from, String to, String currency, Totals totals, List<Month> months, List<CarUse> fleet,
                     List<Category> categories, List<Debtor> debtors) {

    public record Totals(double billed, double collected, double expenses, double net, double outstanding,
                         double fleetUtilizationPercent) {
    }

    public record Month(String month, double billed, double collected, double expenses, double net) {
    }

    public record CarUse(String plate, String car, String category, long nightsRented, long nightsInPeriod,
                         double utilizationPercent, double billed) {
    }

    public record Category(String category, int rentals, double billed) {
    }

    public record Debtor(Long contractRef, String contractId, String clientName, String endDate, double total,
                         double paid, double left) {
    }
}
