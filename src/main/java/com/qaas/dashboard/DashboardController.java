package com.qaas.dashboard;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    DashboardDtos.SummaryResponse summary(@RequestParam(required = false) UUID projectId,
                                           Authentication auth) {
        return service.summary(auth.getName(), projectId);
    }

    @GetMapping("/trends")
    List<DashboardDtos.TrendPoint> trends(@RequestParam(required = false) UUID projectId,
                                           Authentication auth) {
        return service.trends(auth.getName(), projectId);
    }
}