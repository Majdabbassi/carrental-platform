package com.back.car_rent.serviceimpl;

import com.back.car_rent.config.ApiException;
import com.back.car_rent.model.Car;
import com.back.car_rent.model.Contract;
import com.back.car_rent.payments.PaymentRepository;
import com.back.car_rent.payments.PaymentService;
import com.back.car_rent.repository.CarRepository;
import com.back.car_rent.repository.ContractRepository;
import com.back.car_rent.service.ContractService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ContractServiceImpl implements ContractService {

    private static final Set<String> HOLDS_THE_CAR = Set.of("Reserved", "Active");

    private final ContractRepository repo;
    private final CarRepository cars;
    private final PaymentService payments;
    private final PaymentRepository paymentRecords;

    public ContractServiceImpl(ContractRepository repo, CarRepository cars, PaymentService payments,
                               PaymentRepository paymentRecords) {
        this.repo = repo;
        this.cars = cars;
        this.payments = payments;
        this.paymentRecords = paymentRecords;
    }

    @Override
    public List<Contract> findAll() {
        return repo.findAll();
    }

    @Override
    public List<Contract> findAll(Specification<Contract> spec) {
        return repo.findAll(spec);
    }

    @Override
    public Optional<Contract> findById(Long id) {
        return repo.findById(id);
    }

    /**
     * The car row is locked first, then the overlap is checked and the contract saved in the same transaction,
     * so two staff members booking the same car for the same days cannot both succeed.
     */
    @Override
    @Transactional
    public Contract save(Contract contract) {
        LocalDate start = date(contract.getStartDate(), "Start date");
        LocalDate end = date(contract.getEndDate(), "End date");
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("The end date must be after the start date");
        }
        String plate = contract.getLicensePlate();
        if (plate == null || plate.isBlank()) {
            throw ApiException.badRequest("Choose a car");
        }
        if (cars.lockByLicensePlate(plate).isEmpty()) {
            throw ApiException.badRequest("Unknown car " + plate);
        }
        if (HOLDS_THE_CAR.contains(contract.getStatus())) {
            long self = contract.getId() == null ? -1L : contract.getId();
            List<Contract> clashes = repo.findBlocking(plate, start.toString(), end.toString(), self);
            if (!clashes.isEmpty()) {
                Contract other = clashes.get(0);
                throw ApiException.conflict("Car " + plate + " is already booked from " + other.getStartDate()
                        + " to " + other.getEndDate() + " (contract " + other.getContractId() + ")");
            }
        }
        // the payment status follows the recorded payments (and the total, which an edit may change)
        contract.setPaymentStatus(payments.statusOf(contract));
        return repo.save(contract);
    }

    @Override
    public void deleteById(Long id) {
        if (paymentRecords.existsByContractRef(id)) {
            throw ApiException.conflict("This contract has payments; cancel it instead of deleting it");
        }
        repo.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return repo.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Car> availableCars(LocalDate from, LocalDate to, Long excludeContractId) {
        if (!to.isAfter(from)) {
            throw ApiException.badRequest("The end date must be after the start date");
        }
        Set<String> busy = new HashSet<>();
        repo.findAllBlocking(from.toString(), to.toString(), excludeContractId == null ? -1L : excludeContractId)
                .forEach(c -> busy.add(c.getLicensePlate()));
        return cars.findAll().stream()
                .filter(car -> !busy.contains(car.getLicensePlate()))
                .filter(car -> !"Maintenance".equalsIgnoreCase(car.getStatus())) // in the workshop: not for rent
                .toList();
    }

    private static LocalDate date(String value, String label) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(label + " is required");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(label + " must look like 2026-11-02");
        }
    }
}
