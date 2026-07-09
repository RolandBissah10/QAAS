package com.qaas.dashboard;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record SummaryResponse(
            long applicationsAnalyzed,
            long pagesDiscovered,
            long testsExecuted,
            long passedTests,
            long failedTests,
            double passRate,
            long bugCount,
            long criticalBugs
    ) {
    }
}
