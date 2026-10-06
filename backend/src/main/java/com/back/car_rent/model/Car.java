package com.back.car_rent.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cars")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Car {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String make;
    private String model;
    @Column(name = "car_year")
    private Integer year;
    private String licensePlate;
    private String vin;
    private String color;
    private Integer mileage;

    @Column(length = 32)
    private String status; // Available, Rented, Maintenance, Unavailable

    private String category;
    private Double dailyRate;

    private String lastMaintenance;
    private String nextMaintenance;
    @jakarta.persistence.Column(columnDefinition = "LONGTEXT")
    private String imageUrl;

    private Double totalPrice;
    private Double paidAmount;

    private String transmission;
    private String fuelType;
    private String insuranceExpiry;
    private String registrationExpiry;
    private Double weeklyRate;
    private Double monthlyRate;

    private String currentClient;
    private String clientPhone;
    private String clientEmail;
    private String clientLicense;
    private String contractId;
    private String currentRentalId;

    private Integer fuelLevel;
    private String lastUpdate;
    private String currentLocation;
    private Integer currentSpeed;
    private String vinNumber;
    private String returnDate;
    private Double deposit;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "car_id")
    @Builder.Default
    private List<RentalHistory> rentalHistory = new ArrayList<>();
}