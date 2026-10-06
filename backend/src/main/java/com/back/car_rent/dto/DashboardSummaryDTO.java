package com.back.car_rent.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryDTO {
    // Cars
    private long totalCars;
    private long availableCars;
    private long rentedCars;
    private long maintenanceCars;

    // Clients
    private long totalClients;
    private long activeClients;
    private long blacklistedClients;
    private long pendingVerificationClients;

    // Employees
    private long totalEmployees;
    private long activeEmployees;
    private long suspendedEmployees;
    private long onLeaveEmployees;

    // Partners
    private long totalPartners;
    private long activePartners;
    private long suspendedPartners;
    private long pendingPartners;

    // Financials
    private double totalIncome;
}