package com.qaas.dashboard;

import com.qaas.dashboard.DashboardDtos.SummaryResponse;
import com.qaas.dashboard.DashboardDtos.TrendPoint;
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
    SummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/trends")
    List<TrendPoint> trends() {
        return service.trends();
    }
}
