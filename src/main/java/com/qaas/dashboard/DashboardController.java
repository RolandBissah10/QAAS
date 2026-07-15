package com.qaas.dashboard;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    DashboardDtos.SummaryResponse summary(Authentication auth) {
        return service.summary(auth.getName());
    }

    @GetMapping("/trends")
    List<DashboardDtos.TrendPoint> trends(Authentication auth) {
        return service.trends(auth.getName());
    }
}