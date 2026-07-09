package com.qaas.bug;

import java.time.Instant;
import java.util.UUID;

public final class BugDtos {
    private BugDtos() {
    }

    public record BugResponse(
            UUID id,
            UUID executionId,
            UUID analysisId,
            String title,
            String description,
            Severity severity,
            BugStatus status,
            Instant detectedAt
    ) {
        public static BugResponse from(Bug bug) {
            return new BugResponse(
                    bug.getId(),
                    bug.getExecutionId(),
                    bug.getAnalysisId(),
                    bug.getTitle(),
                    bug.getDescription(),
                    bug.getSeverity(),
                    bug.getStatus(),
                    bug.getDetectedAt()
            );
        }
    }
}
