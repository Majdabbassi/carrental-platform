package com.back.car_rent.service;

import com.back.car_rent.model.Car;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface CarService {
    List<Car> findAll();
    Optional<Car> findById(Long id);
    Car save(Car car);
    void deleteById(Long id);
    boolean existsById(Long id);

    // Specification-based search
    List<Car> findAll(Specification<Car> spec);
}