package com.back.car_rent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.back.car_rent.model.Car;
import com.back.car_rent.service.CarService;
import com.back.car_rent.spec.CarSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @PreAuthorize("@perm.canRead('cars')")
    @GetMapping
    public List<Car> getAll() {
        return carService.findAll();
    }

    @PreAuthorize("@perm.canRead('cars')")
    @GetMapping("/search")
    public List<Car> search(
            @RequestParam(required = false) String make,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo
    ) {
        return carService.findAll(
                Specification.allOf(
                        CarSpecifications.hasMake(make),
                        CarSpecifications.hasModel(model),
                        CarSpecifications.hasStatus(status),
                        CarSpecifications.yearGte(yearFrom),
                        CarSpecifications.yearLte(yearTo)
                )
        );
    }

    @PreAuthorize("@perm.canRead('cars')")
    @GetMapping("/{id}")
    public ResponseEntity<Car> getById(@PathVariable Long id) {
        return carService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('cars')")
    @PostMapping
    public Car create(@RequestBody Car car) {
        car.setId(null);
        return carService.save(car);
    }

    @PreAuthorize("@perm.can('cars')")
    @PutMapping("/{id}")
    public ResponseEntity<Car> update(@PathVariable Long id, @RequestBody Car updated) {
        return carService.findById(id)
                .map(existing -> {
                    updated.setId(existing.getId());
                    return ResponseEntity.ok(carService.save(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('cars')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (carService.existsById(id)) {
            carService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}