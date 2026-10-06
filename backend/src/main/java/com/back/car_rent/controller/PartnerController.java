package com.back.car_rent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.back.car_rent.model.Partner;
import com.back.car_rent.service.PartnerService;
import com.back.car_rent.spec.PartnerSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/partners")
public class PartnerController {

    private final PartnerService service;

    public PartnerController(PartnerService service) { this.service = service; }

    @PreAuthorize("@perm.canRead('partners')")
    @GetMapping
    public List<Partner> getAll() { return service.findAll(); }

    @PreAuthorize("@perm.canRead('partners')")
    @GetMapping("/{id}")
    public ResponseEntity<Partner> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('partners')")
    @PostMapping
    public Partner create(@RequestBody Partner partner) { partner.setId(null); return service.save(partner); }

    @PreAuthorize("@perm.can('partners')")
    @PutMapping("/{id}")
    public ResponseEntity<Partner> update(@PathVariable Long id, @RequestBody Partner partner) {
        return service.findById(id).map(existing -> {
            partner.setId(existing.getId());
            return ResponseEntity.ok(service.save(partner));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('partners')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!service.existsById(id)) return ResponseEntity.notFound().build();
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@perm.canRead('partners')")
    @GetMapping("/search")
    public List<Partner> search(
            @RequestParam(required = false) String partnerId,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone
    ) {
        return service.findAll(
                Specification.allOf(
                        PartnerSpecifications.partnerIdContains(partnerId),
                        PartnerSpecifications.companyNameContains(companyName),
                        PartnerSpecifications.typeEquals(type),
                        PartnerSpecifications.statusEquals(status),
                        PartnerSpecifications.emailContains(email),
                        PartnerSpecifications.phoneContains(phone)
                )
        );
    }
}