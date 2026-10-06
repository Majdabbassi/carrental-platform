package com.back.car_rent.serviceimpl;

import com.back.car_rent.model.Car;
import com.back.car_rent.repository.CarRepository;
import com.back.car_rent.service.CarService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CarServiceImpl implements CarService {

    private final CarRepository repo;

    public CarServiceImpl(CarRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Car> findAll() { return repo.findAll(); }

    @Override
    public Optional<Car> findById(Long id) { return repo.findById(id); }

    @Override
    public Car save(Car car) { return repo.save(car); }

    @Override
    public void deleteById(Long id) { repo.deleteById(id); }

    @Override
    public boolean existsById(Long id) { return repo.existsById(id); }

    @Override
    public List<Car> findAll(Specification<Car> spec) {
        return repo.findAll(spec);
    }
}