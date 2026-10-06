package com.back.car_rent.pricing;

import java.util.List;

/** The price of a rental, explained line by line. */
public record Quote(String licensePlate, int days, double baseDailyRate, List<Line> lines, double subtotal,
                    String discountLabel, double discountPercent, double discountAmount, double total,
                    double averageDailyRate) {

    public record Line(String label, String from, String to, int days, double rate, double amount) {
    }
}
