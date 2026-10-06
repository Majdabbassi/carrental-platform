package com.back.car_rent.service;

import com.back.car_rent.model.Client;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface ClientService {
    List<Client> findAll();
    Optional<Client> findById(Long id);
    Client save(Client client);
    void deleteById(Long id);
    boolean existsById(Long id);

    List<Client> findAll(Specification<Client> spec);
}