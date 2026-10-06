package com.back.car_rent.service;

import com.back.car_rent.model.Car;
import com.back.car_rent.model.Contract;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractService {
    List<Contract> findAll();
    List<Contract> findAll(Specification<Contract> spec);
    Optional<Contract> findById(Long id);

    /** Validates the dates and the car and refuses (409) a booking that overlaps another one of the same car. */
    Contract save(Contract contract);
    void deleteById(Long id);
    boolean existsById(Long id);

    /** Cars with no reserved or active contract on the nights [from, to); {@code excludeContractId} may be null. */
    List<Car> availableCars(LocalDate from, LocalDate to, Long excludeContractId);
}
