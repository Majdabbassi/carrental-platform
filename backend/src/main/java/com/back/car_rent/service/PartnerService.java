package com.back.car_rent.service;

import com.back.car_rent.model.Partner;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface PartnerService {
    List<Partner> findAll();
    List<Partner> findAll(Specification<Partner> spec);
    Optional<Partner> findById(Long id);
    Partner save(Partner partner);
    void deleteById(Long id);
    boolean existsById(Long id);
}