package com.back.car_rent.serviceimpl;

import com.back.car_rent.model.Expense;
import com.back.car_rent.repository.ExpenseRepository;
import com.back.car_rent.service.ExpenseService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository repo;

    public ExpenseServiceImpl(ExpenseRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Expense> findAll() {
        return repo.findAll();
    }

    @Override
    public List<Expense> findAll(Specification<Expense> spec) {
        return repo.findAll(spec);
    }

    @Override
    public Optional<Expense> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    public Expense save(Expense expense) {
        return repo.save(expense);
    }

    @Override
    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return repo.existsById(id);
    }
}