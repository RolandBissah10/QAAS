package com.qaas.testsuite;

import com.qaas.execution.ExecutionStatus;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.testsuite.entity.TestSuite;
import com.qaas.testsuite.entity.TestSuiteItemStatus;
import com.qaas.testsuite.entity.TestSuiteMembership;
import com.qaas.testsuite.entity.TestSuiteRun;
import com.qaas.testsuite.entity.TestSuiteRunItem;
import com.qaas.testsuite.entity.TestSuiteRunStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TestSuiteDtos {
    private TestSuiteDtos() {
    }

    public record SuiteRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description
    ) {
    }

    public record ReplaceTestsRequest(
            @NotNull @Size(max = 500) List<@NotNull UUID> testIds
    ) {
    }

    public record TestSummary(UUID id, String name, String type, String targetUrl) {
        public static TestSummary from(GeneratedTest test) {
            return new TestSummary(test.getId(), test.getName(), test.getType(), test.getTargetUrl());
        }

        public static TestSummary from(TestSuiteMembership membership) {
            return from(membership.getTest());
        }
    }

    public record SuiteResponse(
            UUID id,
            UUID projectId,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt,
            List<TestSummary> tests
    ) {
        public static SuiteResponse from(TestSuite suite, List<TestSuiteMembership> memberships) {
            return new SuiteResponse(suite.getId(), suite.getProjectId(), suite.getName(),
                    suite.getDescription(), suite.getCreatedAt(), suite.getUpdatedAt(),
                    memberships.stream().map(TestSummary::from).toList());
        }
    }

    public record SuiteRunResponse(
            UUID id,
            UUID suiteId,
            TestSuiteRunStatus status,
            int totalTests,
            int passedTests,
            int failedTests,
            int errorTests,
            String environmentName,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            List<SuiteRunItemResponse> items
    ) {
        public static SuiteRunResponse from(TestSuiteRun run, List<TestSuiteRunItem> items) {
            return new SuiteRunResponse(run.getId(), run.getSuite().getId(), run.getStatus(),
                    run.getTotalTests(), run.getPassedTests(), run.getFailedTests(), run.getErrorTests(),
                    run.getEnvironmentName(), run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt(),
                    items.stream().map(SuiteRunItemResponse::from).toList());
        }
    }

    public record SuiteRunSummaryResponse(
            UUID id,
            UUID suiteId,
            TestSuiteRunStatus status,
            int totalTests,
            int passedTests,
            int failedTests,
            int errorTests,
            String environmentName,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt
    ) {
        public static SuiteRunSummaryResponse from(TestSuiteRun run) {
            return new SuiteRunSummaryResponse(run.getId(), run.getSuite().getId(), run.getStatus(),
                    run.getTotalTests(), run.getPassedTests(), run.getFailedTests(), run.getErrorTests(),
                    run.getEnvironmentName(), run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt());
        }
    }

    public record SuiteRunItemResponse(
            UUID id,
            UUID testId,
            String testName,
            TestSuiteItemStatus status,
            UUID executionId,
            ExecutionStatus executionStatus,
            String errorMessage
    ) {
        public static SuiteRunItemResponse from(TestSuiteRunItem item) {
            return new SuiteRunItemResponse(
                    item.getId(),
                    item.getTest().getId(),
                    item.getTestName(),
                    item.getStatus(),
                    item.getExecution() == null ? null : item.getExecution().getId(),
                    item.getExecution() == null ? null : item.getExecution().getStatus(),
                    item.getErrorMessage());
        }
    }

    public record SuiteRunWorkItem(
            UUID id,
            GeneratedTest test,
            UUID analysisId,
            String targetUrl,
            String authUsername,
            String authPassword,
            boolean environmentSelected
    ) {
    }
}
