package com.back.car_rent.spec;

import com.back.car_rent.model.Car;
import org.springframework.data.jpa.domain.Specification;

public class CarSpecifications {

    public static Specification<Car> hasMake(String make) {
        return (root, query, cb) -> make == null || make.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("make")), "%" + make.toLowerCase() + "%");
    }

    public static Specification<Car> hasModel(String model) {
        return (root, query, cb) -> model == null || model.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("model")), "%" + model.toLowerCase() + "%");
    }

    public static Specification<Car> hasStatus(String status) {
        return (root, query, cb) -> status == null || status.isBlank()
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("status")), status.toLowerCase());
    }

    public static Specification<Car> yearGte(Integer year) {
        return (root, query, cb) -> year == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("year"), year);
    }

    public static Specification<Car> yearLte(Integer year) {
        return (root, query, cb) -> year == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("year"), year);
    }
}