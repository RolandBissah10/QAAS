package com.qaas.report;

import java.time.Instant;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record ReportResponse(
            UUID id,
            UUID analysisId,
            ReportFormat format,
            String filePath,
            Instant generatedAt,
            Integer qualityScore,
            Integer totalTests,
            Integer passedTests,
            Integer failedTests,
            Integer bugCount,
            Integer pagesDiscovered
    ) {
        public static ReportResponse from(Report r) {
            return new ReportResponse(
                    r.getId(), r.getAnalysisId(), r.getFormat(), r.getFilePath(),
                    r.getGeneratedAt(), r.getQualityScore(), r.getTotalTests(),
                    r.getPassedTests(), r.getFailedTests(), r.getBugCount(), r.getPagesDiscovered()
            );
        }
    }

    public record GenerateReportRequest(ReportFormat format) {
    }
}
