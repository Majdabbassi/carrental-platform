package com.back.car_rent.controller;

import com.back.car_rent.model.Car;
import com.back.car_rent.service.ContractService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final ContractService contracts;

    public AvailabilityController(ContractService contracts) {
        this.contracts = contracts;
    }

    /**
     * Cars that are free for the nights from {@code from} to {@code to} (the return day itself is free again).
     * {@code excludeContract} is the id of a contract being edited, so it does not block its own car.
     */
    @PreAuthorize("@perm.canRead('cars')")
    @GetMapping("/cars")
    public List<Car> cars(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) Long excludeContract) {
        return contracts.availableCars(from, to, excludeContract);
    }
}
