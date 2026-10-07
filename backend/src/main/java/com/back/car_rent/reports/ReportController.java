package com.back.car_rent.reports;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Reports section: billed vs collected vs expenses per month, fleet use, categories and who still owes money. */
@RestController
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** {@code from} and {@code to} are months (2026-03); by default the last six months up to this one. */
    @PreAuthorize("@perm.can('reports')")
    @GetMapping("/api/reports/summary")
    public Report summary(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return reports.summary(from, to);
    }
}
