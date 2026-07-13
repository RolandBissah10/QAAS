package com.qaas.dashboard;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record SummaryResponse(
            long applicationsAnalyzed,
            long pagesDiscovered,
            long testsExecuted,
            long passedTests,
            long failedTests,
            double passRate,
            long bugCount,
            long criticalBugs,
            long highBugs,
            long mediumBugs,
            long lowBugs
    ) {}

    public record TrendPoint(
            String analysisId,
            String analysisUrl,
            String projectId,
            String projectName,
            String date,
            Integer qualityScore,
            Integer passedTests,
            Integer failedTests,
            Integer bugCount,
            Integer pagesDiscovered
    ) {}
}
