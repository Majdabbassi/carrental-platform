package com.back.car_rent.serviceimpl;

import com.back.car_rent.model.Client;
import com.back.car_rent.repository.ClientRepository;
import com.back.car_rent.service.ClientService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository repo;

    public ClientServiceImpl(ClientRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Client> findAll() { return repo.findAll(); }

    @Override
    public Optional<Client> findById(Long id) { return repo.findById(id); }

    @Override
    public Client save(Client client) { return repo.save(client); }

    @Override
    public void deleteById(Long id) { repo.deleteById(id); }

    @Override
    public boolean existsById(Long id) { return repo.existsById(id); }

    @Override
    public List<Client> findAll(Specification<Client> spec) { return repo.findAll(spec); }
}