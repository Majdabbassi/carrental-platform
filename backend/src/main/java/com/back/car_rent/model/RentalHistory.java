package com.back.car_rent.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rental_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String startDate;
    private String endDate;
    private String clientId;
    private Double totalPrice;
    private Double paidAmount;
    private String status;
    private String clientName;
    private String contractId;
}