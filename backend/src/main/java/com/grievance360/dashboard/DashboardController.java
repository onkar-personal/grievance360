package com.grievance360.dashboard;

import com.grievance360.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Dashboards & reports")
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/dashboard/citizen")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Citizen's own complaint statistics")
    public DashboardService.CitizenDashboard citizen() {
        return service.citizen(CurrentUser.get());
    }

    @GetMapping("/officer/dashboard")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Officer workload and performance")
    public DashboardService.OfficerDashboard officer() {
        return service.officer(CurrentUser.get());
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "System-wide aggregates")
    public DashboardService.Overview admin(@RequestParam(defaultValue = "30") int days) {
        return service.adminOverview(days);
    }

    @GetMapping("/admin/reports/sla")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "SLA compliance by category for complaints created in a date range")
    public List<DashboardService.CategoryRow> sla(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.slaByCategory(from, to);
    }
}
