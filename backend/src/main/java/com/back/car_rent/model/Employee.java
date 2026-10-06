package com.back.car_rent.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employees")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeId;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String status; // Active, On Leave, Suspended, Terminated
    private String dateJoined;
    private String birthdate;
    private String gender;
    private String address;
    private String department;
    private Double salary;
    private String notes;
    @jakarta.persistence.Column(columnDefinition = "LONGTEXT")
    private String profilePicture;

    @Embedded
    private AccessRights accessRights;
}