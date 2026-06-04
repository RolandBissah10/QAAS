package com.qaas.result;

import com.qaas.execution.ExecutionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ResultDtos {
    private ResultDtos() {
    }

    public record ResultResponse(
            UUID id,
            UUID executionId,
            ExecutionStatus status,
            long responseTime,
            Map<String, Object> responseBody,
            boolean passed,
            Instant executedAt
    ) {
        public static ResultResponse from(TestResult result) {
            return new ResultResponse(
                    result.getId(),
                    result.getExecution().getId(),
                    result.getExecution().getStatus(),
                    result.getResponseTime(),
                    result.getResponseBody(),
                    result.isPassed(),
                    result.getExecution().getExecutedAt()
            );
        }
    }
}
