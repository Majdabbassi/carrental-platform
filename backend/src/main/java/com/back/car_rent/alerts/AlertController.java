package com.back.car_rent.alerts;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @PreAuthorize("@perm.canRead('dashboard')")
    @GetMapping
    public List<Alert> open() {
        return service.open();
    }

    @PreAuthorize("@perm.can('dashboard')")
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable Long id) {
        return service.dismiss(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Runs the daily check now (it also runs every morning). */
    @PreAuthorize("@perm.can('dashboard')")
    @PostMapping("/refresh")
    public Map<String, Integer> refresh() {
        return Map.of("created", service.refresh());
    }
}
