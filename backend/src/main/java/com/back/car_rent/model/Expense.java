package com.back.car_rent.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "expenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String expenseId;
    private String category;
    private String description;
    private Double amount;
    private String date;
    private String paidBy;
    private String status;
    private String employee;
    private String linkedCar;
    @jakarta.persistence.Column(columnDefinition = "LONGTEXT")
    private String invoice;
    private String notes;
    private String statusBg;
}