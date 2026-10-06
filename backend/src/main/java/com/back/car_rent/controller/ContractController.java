package com.back.car_rent.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.back.car_rent.model.Contract;
import com.back.car_rent.document.ContractDocumentService;
import com.back.car_rent.service.ContractService;
import com.back.car_rent.spec.ContractSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService service;
    private final ContractDocumentService documents;

    public ContractController(ContractService service, ContractDocumentService documents) {
        this.service = service;
        this.documents = documents;
    }

    /** The printable rental agreement ({@code type=contract}, default) or the invoice ({@code type=invoice}). */
    @PreAuthorize("@perm.canRead('contracts')")
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @RequestParam(required = false) String type) {
        ContractDocumentService.Kind kind = ContractDocumentService.kind(type);
        return service.findById(id).map(contract -> {
            String name = (kind == ContractDocumentService.Kind.INVOICE ? "invoice-" : "contract-")
                    + String.valueOf(contract.getContractId()).replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(name).build().toString())
                    .body(documents.render(contract, kind));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.canRead('contracts')")
    @GetMapping
    public List<Contract> getAll() { return service.findAll(); }

    @PreAuthorize("@perm.canRead('contracts')")
    @GetMapping("/{id}")
    public ResponseEntity<Contract> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('contracts')")
    @PostMapping
    public Contract create(@RequestBody Contract contract) { contract.setId(null); return service.save(contract); }

    @PreAuthorize("@perm.can('contracts')")
    @PutMapping("/{id}")
    public ResponseEntity<Contract> update(@PathVariable Long id, @RequestBody Contract contract) {
        return service.findById(id).map(existing -> {
            contract.setId(existing.getId());
            return ResponseEntity.ok(service.save(contract));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("@perm.can('contracts')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!service.existsById(id)) return ResponseEntity.notFound().build();
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@perm.canRead('contracts')")
    @GetMapping("/search")
    public List<Contract> search(
            @RequestParam(required = false) String contractId,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String carMake,
            @RequestParam(required = false) String carModel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "paymentStatus") String payStatus,
            @RequestParam(required = false) String rentalType
    ) {
        return service.findAll(
                Specification.allOf(
                        ContractSpecifications.contractIdContains(contractId),
                        ContractSpecifications.clientNameContains(clientName),
                        ContractSpecifications.phoneContains(phone),
                        ContractSpecifications.carMakeContains(carMake),
                        ContractSpecifications.carModelContains(carModel),
                        ContractSpecifications.statusEquals(status),
                        ContractSpecifications.paymentStatusEquals(payStatus),
                        ContractSpecifications.rentalTypeEquals(rentalType)
                )
        );
    }
}