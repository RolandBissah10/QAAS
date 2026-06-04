package com.qaas.dashboard;

import com.qaas.dashboard.DashboardDtos.SummaryResponse;
import com.qaas.dashboard.DashboardDtos.TrendPoint;
import com.qaas.execution.ExecutionStatus;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.result.TestResultRepository;
import com.qaas.test.ApiTestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DashboardService {
    private final ApiTestRepository tests;
    private final TestExecutionRepository executions;
    private final TestResultRepository results;

    public DashboardService(ApiTestRepository tests, TestExecutionRepository executions, TestResultRepository results) {
        this.tests = tests;
        this.executions = executions;
        this.results = results;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary() {
        long totalTests = tests.count();
        long passed = executions.countByStatus(ExecutionStatus.PASSED);
        long failed = executions.countByStatus(ExecutionStatus.FAILED) + executions.countByStatus(ExecutionStatus.ERROR);
        long totalRuns = passed + failed;
        double passRate = totalRuns == 0 ? 0 : (passed * 100.0) / totalRuns;
        return new SummaryResponse(totalTests, passed, failed, passRate, results.averageResponseTime());
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> trends() {
        return executions.findTop30ByExecutedAtAfterOrderByExecutedAtAsc(Instant.now().minus(30, ChronoUnit.DAYS))
                .stream()
                .map(execution -> new TrendPoint(execution.getExecutedAt(), execution.getStatus()))
                .toList();
    }
}
