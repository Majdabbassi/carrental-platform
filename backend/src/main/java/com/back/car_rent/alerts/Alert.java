package com.back.car_rent.alerts;

import jakarta.persistence.*;
import lombok.*;

/** Something the agency must act on soon: an expiring document, a service due, a car that is late coming back. */
@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What and which car or contract, e.g. "insurance:TN-2188-E:2026-10-17"; one alert per key. */
    @Column(name = "ref_key", unique = true, nullable = false, length = 190)
    private String refKey;

    private String type;      // INSURANCE, REGISTRATION, MAINTENANCE, OVERDUE_RETURN, RETURN_DUE
    private String severity;  // CRITICAL, WARNING, INFO
    private String title;
    private String message;
    private String dueDate;
    private boolean dismissed;
    private String createdAt;
}
