package com.back.car_rent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.back.car_rent.dto.DashboardSummaryDTO;
import com.back.car_rent.model.Car;
import com.back.car_rent.model.Client;
import com.back.car_rent.model.Contract;
import com.back.car_rent.model.Employee;
import com.back.car_rent.model.Partner;
import com.back.car_rent.repository.CarRepository;
import com.back.car_rent.repository.ClientRepository;
import com.back.car_rent.repository.ContractRepository;
import com.back.car_rent.repository.EmployeeRepository;
import com.back.car_rent.repository.PartnerRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CarRepository carRepository;
    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final PartnerRepository partnerRepository;
    private final ContractRepository contractRepository;

    public DashboardController(CarRepository carRepository, ClientRepository clientRepository,
                               EmployeeRepository employeeRepository, PartnerRepository partnerRepository,
                               ContractRepository contractRepository) {
        this.carRepository = carRepository;
        this.clientRepository = clientRepository;
        this.employeeRepository = employeeRepository;
        this.partnerRepository = partnerRepository;
        this.contractRepository = contractRepository;
    }

    @PreAuthorize("@perm.canRead('dashboard')")
    @GetMapping("/summary")
    public DashboardSummaryDTO getSummary() {
        List<Car> cars = carRepository.findAll();
        List<Client> clients = clientRepository.findAll();
        List<Employee> employees = employeeRepository.findAll();
        List<Partner> partners = partnerRepository.findAll();
        List<Contract> contracts = contractRepository.findAll();

        long availableCars = cars.stream().filter(c -> "Available".equalsIgnoreCase(c.getStatus())).count();
        long rentedCars = cars.stream().filter(c -> "Rented".equalsIgnoreCase(c.getStatus())).count();
        long maintenanceCars = cars.stream().filter(c -> c.getStatus() != null && c.getStatus().toLowerCase().contains("maintenance")).count();

        long activeClients = clients.stream().filter(c -> "Active".equalsIgnoreCase(c.getStatus())).count();
        long blacklistedClients = clients.stream().filter(c -> "Blacklisted".equalsIgnoreCase(c.getStatus())).count();
        long pendingVerificationClients = clients.stream().filter(c -> "Pending Verification".equalsIgnoreCase(c.getStatus())).count();

        long activeEmployees = employees.stream().filter(e -> "Active".equalsIgnoreCase(e.getStatus())).count();
        long suspendedEmployees = employees.stream().filter(e -> "Suspended".equalsIgnoreCase(e.getStatus())).count();
        long onLeaveEmployees = employees.stream().filter(e -> "On Leave".equalsIgnoreCase(e.getStatus())).count();

        long activePartners = partners.stream().filter(p -> "Active".equalsIgnoreCase(p.getStatus())).count();
        long suspendedPartners = partners.stream().filter(p -> "Suspended".equalsIgnoreCase(p.getStatus())).count();
        long pendingPartners = partners.stream().filter(p -> "Pending Approval".equalsIgnoreCase(p.getStatus())).count();

        double totalIncome = contracts.stream()
                .filter(c -> "Completed".equalsIgnoreCase(c.getStatus()) || "Paid".equalsIgnoreCase(c.getPaymentStatus()))
                .mapToDouble(c -> c.getTotalValue() != null ? c.getTotalValue() : 0.0)
                .sum();

        return DashboardSummaryDTO.builder()
                .totalCars(cars.size())
                .availableCars(availableCars)
                .rentedCars(rentedCars)
                .maintenanceCars(maintenanceCars)
                .totalClients(clients.size())
                .activeClients(activeClients)
                .blacklistedClients(blacklistedClients)
                .pendingVerificationClients(pendingVerificationClients)
                .totalEmployees(employees.size())
                .activeEmployees(activeEmployees)
                .suspendedEmployees(suspendedEmployees)
                .onLeaveEmployees(onLeaveEmployees)
                .totalPartners(partners.size())
                .activePartners(activePartners)
                .suspendedPartners(suspendedPartners)
                .pendingPartners(pendingPartners)
                .totalIncome(totalIncome)
                .build();
    }
}