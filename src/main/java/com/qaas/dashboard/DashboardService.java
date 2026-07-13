package com.qaas.dashboard;

import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.bug.BugRepository;
import com.qaas.bug.Severity;
import com.qaas.execution.ExecutionStatus;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.page.repository.PageRepository;
import com.qaas.project.entity.Project;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.report.Report;
import com.qaas.report.ReportFormat;
import com.qaas.report.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());

    private final AnalysisRepository analyses;
    private final PageRepository pages;
    private final TestExecutionRepository executions;
    private final BugRepository bugs;
    private final ReportRepository reports;
    private final ProjectRepository projects;

    public DashboardService(AnalysisRepository analyses, PageRepository pages,
                            TestExecutionRepository executions, BugRepository bugs,
                            ReportRepository reports, ProjectRepository projects) {
        this.analyses = analyses;
        this.pages = pages;
        this.executions = executions;
        this.bugs = bugs;
        this.reports = reports;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.SummaryResponse summary() {
        long appCount     = analyses.count();
        long pageCount    = pages.count();
        long passed       = executions.countByStatus(ExecutionStatus.PASSED);
        long failed       = executions.countByStatus(ExecutionStatus.FAILED)
                           + executions.countByStatus(ExecutionStatus.ERROR);
        long total        = passed + failed;
        double passRate   = total == 0 ? 0.0 : (passed * 100.0) / total;
        long bugCount     = bugs.count();
        long critical     = bugs.countBySeverity(Severity.CRITICAL);
        long high         = bugs.countBySeverity(Severity.HIGH);
        long medium       = bugs.countBySeverity(Severity.MEDIUM);
        long low          = bugs.countBySeverity(Severity.LOW);

        return new DashboardDtos.SummaryResponse(
                appCount, pageCount, total, passed, failed,
                passRate, bugCount, critical, high, medium, low
        );
    }

    @Transactional(readOnly = true)
    public List<DashboardDtos.TrendPoint> trends() {
        List<Report> recent = reports.findTop20ByFormatOrderByGeneratedAtDesc(ReportFormat.JSON);
        if (recent.isEmpty()) return List.of();

        // Batch-load analyses and projects to avoid N+1
        Set<UUID> analysisIds = recent.stream().map(Report::getAnalysisId).collect(Collectors.toSet());
        Map<UUID, Analysis> analysisMap = analyses.findAllById(analysisIds)
                .stream().collect(Collectors.toMap(Analysis::getId, a -> a));

        Set<UUID> projectIds = analysisMap.values().stream()
                .map(Analysis::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> projectNames = projects.findAllById(projectIds)
                .stream().collect(Collectors.toMap(Project::getId, Project::getName));

        List<DashboardDtos.TrendPoint> result = new ArrayList<>();
        for (Report r : recent) {
            Analysis a = analysisMap.get(r.getAnalysisId());
            if (a == null) continue;
            String projectId   = a.getProjectId() != null ? a.getProjectId().toString() : null;
            String projectName = projectId != null ? projectNames.getOrDefault(a.getProjectId(), "Unknown") : "Unknown";
            result.add(new DashboardDtos.TrendPoint(
                    r.getAnalysisId().toString(),
                    a.getUrl(),
                    projectId,
                    projectName,
                    DATE_FMT.format(r.getGeneratedAt()),
                    r.getQualityScore(),
                    r.getPassedTests(),
                    r.getFailedTests(),
                    r.getBugCount(),
                    r.getPagesDiscovered()
            ));
        }
        // Return chronological order (oldest first) so charts render left→right
        Collections.reverse(result);
        return result;
    }
}