package com.back.car_rent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.back.car_rent.model.Expense;
import com.back.car_rent.service.ExpenseService;
import com.back.car_rent.spec.ExpenseSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PreAuthorize("@perm.canRead('expenses')")
    @GetMapping
    public List<Expense> getAll() {
        return service.findAll();
    }

    @PreAuthorize("@perm.canRead('expenses')")
    @GetMapping("/{id}")
    public ResponseEntity<Expense> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('expenses')")
    @PostMapping
    public Expense create(@RequestBody Expense expense) {
        expense.setId(null);
        return service.save(expense);
    }

    @PreAuthorize("@perm.can('expenses')")
    @PutMapping("/{id}")
    public ResponseEntity<Expense> update(@PathVariable Long id, @RequestBody Expense updated) {
        return service.findById(id)
                .map(existing -> {
                    updated.setId(existing.getId());
                    return ResponseEntity.ok(service.save(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('expenses')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (service.existsById(id)) {
            service.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PreAuthorize("@perm.canRead('expenses')")
    @GetMapping("/search")
    public List<Expense> search(
            @RequestParam(required = false) String expenseId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String paidBy,
            @RequestParam(required = false) String status
    ) {
        return service.findAll(
                Specification.allOf(
                        ExpenseSpecifications.expenseIdContains(expenseId),
                        ExpenseSpecifications.categoryEquals(category),
                        ExpenseSpecifications.descriptionContains(description),
                        ExpenseSpecifications.paidByContains(paidBy),
                        ExpenseSpecifications.statusEquals(status)
                )
        );
    }
}