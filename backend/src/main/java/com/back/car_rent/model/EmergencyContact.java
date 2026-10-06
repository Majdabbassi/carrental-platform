package com.back.car_rent.model;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class EmergencyContact {
    private String name;
    private String phone;
    private String relationship;
}