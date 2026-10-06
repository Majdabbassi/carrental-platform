package com.back.car_rent.pricing;

import com.back.car_rent.config.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PricingController {

    private final PricingService pricing;
    private final PricingRuleRepository rules;

    public PricingController(PricingService pricing, PricingRuleRepository rules) {
        this.pricing = pricing;
        this.rules = rules;
    }

    /** What a rental of this car for these dates costs, line by line. Open to whoever writes contracts. */
    @PreAuthorize("@perm.canRead('contracts')")
    @GetMapping("/api/pricing/quote")
    public Quote quote(@RequestParam String plate, @RequestParam String from, @RequestParam String to) {
        return pricing.quote(plate, from, to);
    }

    // The rules themselves are for administrators only.

    @PreAuthorize("@perm.isAdmin()")
    @GetMapping("/api/pricing-rules")
    public List<PricingRule> list() {
        return rules.findAll();
    }

    @PreAuthorize("@perm.isAdmin()")
    @PostMapping("/api/pricing-rules")
    public PricingRule create(@RequestBody PricingRule rule) {
        rule.setId(null);
        pricing.validate(rule);
        return rules.save(rule);
    }

    @PreAuthorize("@perm.isAdmin()")
    @PutMapping("/api/pricing-rules/{id}")
    public PricingRule update(@PathVariable Long id, @RequestBody PricingRule rule) {
        if (!rules.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found");
        }
        rule.setId(id);
        pricing.validate(rule);
        return rules.save(rule);
    }

    @PreAuthorize("@perm.isAdmin()")
    @DeleteMapping("/api/pricing-rules/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!rules.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        rules.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
