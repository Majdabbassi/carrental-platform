package com.back.car_rent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.back.car_rent.model.Client;
import com.back.car_rent.service.ClientService;
import com.back.car_rent.spec.ClientSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @PreAuthorize("@perm.canRead('clients')")
    @GetMapping
    public List<Client> getAll() { return service.findAll(); }

    @PreAuthorize("@perm.canRead('clients')")
    @GetMapping("/search")
    public List<Client> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status
    ) {
        return service.findAll(
                Specification.allOf(
                        ClientSpecifications.fullNameContains(name),
                        ClientSpecifications.emailContains(email),
                        ClientSpecifications.phoneContains(phone),
                        ClientSpecifications.statusEquals(status)
                )
        );
    }

    @PreAuthorize("@perm.canRead('clients')")
    @GetMapping("/{id}")
    public ResponseEntity<Client> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('clients')")
    @PostMapping
    public Client create(@RequestBody Client client) { client.setId(null); return service.save(client); }

    @PreAuthorize("@perm.can('clients')")
    @PutMapping("/{id}")
    public ResponseEntity<Client> update(@PathVariable Long id, @RequestBody Client client) {
        return service.findById(id).map(existing -> {
            client.setId(existing.getId());
            return ResponseEntity.ok(service.save(client));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('clients')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!service.existsById(id)) return ResponseEntity.notFound().build();
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}