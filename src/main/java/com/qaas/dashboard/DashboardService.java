package com.qaas.dashboard;

import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.bug.BugRepository;
import com.qaas.bug.Severity;
import com.qaas.execution.ExecutionStatus;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.page.repository.PageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final AnalysisRepository analyses;
    private final PageRepository pages;
    private final TestExecutionRepository executions;
    private final BugRepository bugs;

    public DashboardService(AnalysisRepository analyses, PageRepository pages,
                            TestExecutionRepository executions, BugRepository bugs) {
        this.analyses = analyses;
        this.pages = pages;
        this.executions = executions;
        this.bugs = bugs;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.SummaryResponse summary() {
        long appCount = analyses.count();
        long pageCount = pages.count();
        long passed = executions.countByStatus(ExecutionStatus.PASSED);
        long failed = executions.countByStatus(ExecutionStatus.FAILED)
                + executions.countByStatus(ExecutionStatus.ERROR);
        long total = passed + failed;
        double passRate = total == 0 ? 0.0 : (passed * 100.0) / total;
        long bugCount = bugs.count();
        long criticalBugs = bugs.countBySeverity(Severity.CRITICAL);

        return new DashboardDtos.SummaryResponse(
                appCount, pageCount, total, passed, failed, passRate, bugCount, criticalBugs
        );
    }
}
