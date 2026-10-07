package com.back.car_rent.payments;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The Payments section: whoever holds the "payments" right records and corrects the money received. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PreAuthorize("@perm.canRead('payments')")
    @GetMapping
    public List<Payment> list(@RequestParam(required = false) Long contractRef) {
        return contractRef == null ? service.findAll() : service.forContract(contractRef);
    }

    @PreAuthorize("@perm.can('payments')")
    @PostMapping
    public Payment record(@RequestBody Payment payment) {
        payment.setId(null);
        payment.setPaymentId(null);
        return service.record(payment);
    }

    @PreAuthorize("@perm.can('payments')")
    @PutMapping("/{id}")
    public Payment update(@PathVariable Long id, @RequestBody Payment payment) {
        return service.update(id, payment);
    }

    @PreAuthorize("@perm.can('payments')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
