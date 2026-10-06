package com.back.car_rent.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contracts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id")
    private String contractId; // e.g., CT-001

    private String clientName;
    private String clientPhone;
    private String carMake;
    private String carModel;
    private String licensePlate;
    private String rentalType; // Daily, Weekly, Monthly, Long-Term
    private String startDate;
    private String endDate;
    private Double dailyRate;
    private Double totalValue;
    private Double deposit;
    private String status; // Reserved, Active, Completed, Canceled
    private String paymentStatus; // Pending, Partial, Paid

    // Optional references
    private String client;
    private String car;
    private String paymentMethod;
    private String notes;
}