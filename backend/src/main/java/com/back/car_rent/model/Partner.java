package com.back.car_rent.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "partners")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String partnerId;
    private String companyName;

    @Column(name = "partner_type")
    private String type; // Maintenance, Insurance, Rental Agency, Fuel Supplier, Other

    private String contactPerson;
    private String phone;
    private String email;
    private String status; // Active, Suspended, Pending Approval
    private String address;
    private String website;
    private String partnershipStartDate;
    private String agreementReference;
    private String notes;
    @jakarta.persistence.Column(columnDefinition = "LONGTEXT")
    private String agreementFile;
}