package com.back.car_rent.spec;

import com.back.car_rent.model.Expense;
import org.springframework.data.jpa.domain.Specification;

public class ExpenseSpecifications {

    public static Specification<Expense> expenseIdContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("expenseId")), like);
    }

    public static Specification<Expense> categoryEquals(String category) {
        if (category == null || category.isBlank()) return null;
        String s = category.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), s);
    }

    public static Specification<Expense> descriptionContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), like);
    }

    public static Specification<Expense> paidByContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("paidBy")), like);
    }

    public static Specification<Expense> statusEquals(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("status")), s);
    }
}