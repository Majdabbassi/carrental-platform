package com.back.car_rent.serviceimpl;

import com.back.car_rent.model.Partner;
import com.back.car_rent.repository.PartnerRepository;
import com.back.car_rent.service.PartnerService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PartnerServiceImpl implements PartnerService {

    private final PartnerRepository repo;

    public PartnerServiceImpl(PartnerRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Partner> findAll() {
        return repo.findAll();
    }

    @Override
    public List<Partner> findAll(Specification<Partner> spec) {
        return repo.findAll(spec);
    }

    @Override
    public Optional<Partner> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    public Partner save(Partner partner) {
        return repo.save(partner);
    }

    @Override
    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return repo.existsById(id);
    }
}