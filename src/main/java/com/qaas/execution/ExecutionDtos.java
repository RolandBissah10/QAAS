package com.qaas.execution;

import java.time.Instant;
import java.util.UUID;

public final class ExecutionDtos {
    private ExecutionDtos() {
    }

    public record ExecutionResponse(
            UUID id,
            UUID testId,
            String testName,
            ExecutionStatus status,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
        public static ExecutionResponse from(TestExecution e) {
            return new ExecutionResponse(
                    e.getId(),
                    e.getTest().getId(),
                    e.getTest().getName(),
                    e.getStatus(),
                    e.getStartedAt(),
                    e.getCompletedAt(),
                    e.getErrorMessage()
            );
        }
    }
}
