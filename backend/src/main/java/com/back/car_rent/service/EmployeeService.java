package com.back.car_rent.service;

import com.back.car_rent.model.Employee;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface EmployeeService {
    List<Employee> findAll();
    List<Employee> findAll(Specification<Employee> spec);
    Optional<Employee> findById(Long id);
    Employee save(Employee employee);
    void deleteById(Long id);
    boolean existsById(Long id);
}