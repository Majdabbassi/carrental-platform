package com.back.car_rent.spec;

import com.back.car_rent.model.Employee;
import org.springframework.data.jpa.domain.Specification;

public class EmployeeSpecifications {

    public static Specification<Employee> nameContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("fullName")), like);
    }

    public static Specification<Employee> emailContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("email")), like);
    }

    public static Specification<Employee> phoneContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("phone")), like);
    }

    public static Specification<Employee> roleEquals(String role) {
        if (role == null || role.isBlank()) return null;
        String s = role.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("role")), s);
    }

    public static Specification<Employee> statusEquals(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("status")), s);
    }

    public static Specification<Employee> departmentEquals(String department) {
        if (department == null || department.isBlank()) return null;
        String s = department.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("department")), s);
    }
}