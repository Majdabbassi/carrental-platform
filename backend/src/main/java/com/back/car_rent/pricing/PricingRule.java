package com.back.car_rent.pricing;

import jakarta.persistence.*;
import lombok.*;

/**
 * SEASON: the daily rate is multiplied by {@code multiplier} on every night from startDate to endDate (both
 * included), optionally only for one car category. LONG_STAY: rentals of at least {@code minDays} days get
 * {@code discountPercent} off the total.
 */
@Entity
@Table(name = "pricing_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String type;            // SEASON or LONG_STAY
    private String startDate;       // SEASON, ISO date
    private String endDate;         // SEASON, ISO date
    private String category;        // empty = every category
    private Double multiplier;      // SEASON, e.g. 1.3
    private Integer minDays;        // LONG_STAY
    private Double discountPercent; // LONG_STAY, e.g. 10
}
