package com.back.car_rent.repository;

import com.back.car_rent.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    java.util.Optional<com.back.car_rent.model.Employee> findByEmailIgnoreCase(String email);
}
