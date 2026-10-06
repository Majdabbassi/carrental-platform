package com.back.car_rent.service;

import com.back.car_rent.model.Expense;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface ExpenseService {
    List<Expense> findAll();
    List<Expense> findAll(Specification<Expense> spec);
    Optional<Expense> findById(Long id);
    Expense save(Expense expense);
    void deleteById(Long id);
    boolean existsById(Long id);
}