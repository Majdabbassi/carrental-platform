package com.back.car_rent.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;
    private String fullName;
    private String email;
    private String phone;
    private String dateOfBirth;
    private String gender;
    private String address;
    private String drivingLicenseNumber;
    private String licenseExpiryDate;
    private String nationalId;
    private String status;
    private String registrationDate;
    private Integer totalRentals;
    @jakarta.persistence.Column(columnDefinition = "LONGTEXT")
    private String avatar;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "emergency_name")),
            @AttributeOverride(name = "phone", column = @Column(name = "emergency_phone")),
            @AttributeOverride(name = "relationship", column = @Column(name = "emergency_relationship"))
    })
    private EmergencyContact emergencyContact;

    private String notes;
}