package com.back.car_rent.model;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class AccessRights {
    private boolean dashboard;
    private boolean cars;
    private boolean clients;
    private boolean contracts;
    private boolean payments;
    private boolean partners;
    private boolean expenses;
    private boolean reports;
}