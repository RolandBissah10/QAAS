package com.qaas.dashboard;

import com.qaas.execution.ExecutionStatus;

import java.time.Instant;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record SummaryResponse(long totalTests, long passedTests, long failedTests, double passRate, double averageResponseTime) {
    }

    public record TrendPoint(Instant executedAt, ExecutionStatus status) {
    }
}
