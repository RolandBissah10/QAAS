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
import com.qaas.user.UserRepository;
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
    private final UserRepository users;

    public DashboardService(AnalysisRepository analyses, PageRepository pages,
                            TestExecutionRepository executions, BugRepository bugs,
                            ReportRepository reports, ProjectRepository projects,
                            UserRepository users) {
        this.analyses = analyses;
        this.pages = pages;
        this.executions = executions;
        this.bugs = bugs;
        this.reports = reports;
        this.projects = projects;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.SummaryResponse summary(String userEmail) {
        List<UUID> analysisIds = resolveAnalysisIds(userEmail);
        if (analysisIds.isEmpty()) {
            return new DashboardDtos.SummaryResponse(0, 0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0);
        }

        long appCount   = analysisIds.size();
        long pageCount  = pages.countByAnalysisIdIn(analysisIds);
        long passed     = executions.countByStatusAndAnalysisIds(ExecutionStatus.PASSED, analysisIds);
        long failed     = executions.countByStatusAndAnalysisIds(ExecutionStatus.FAILED, analysisIds)
                        + executions.countByStatusAndAnalysisIds(ExecutionStatus.ERROR, analysisIds);
        long total      = passed + failed;
        double passRate = total == 0 ? 0.0 : (passed * 100.0) / total;
        long bugCount   = bugs.countByAnalysisIdIn(analysisIds);
        long critical   = bugs.countBySeverityAndAnalysisIdIn(Severity.CRITICAL, analysisIds);
        long high       = bugs.countBySeverityAndAnalysisIdIn(Severity.HIGH, analysisIds);
        long medium     = bugs.countBySeverityAndAnalysisIdIn(Severity.MEDIUM, analysisIds);
        long low        = bugs.countBySeverityAndAnalysisIdIn(Severity.LOW, analysisIds);

        return new DashboardDtos.SummaryResponse(
                appCount, pageCount, total, passed, failed,
                passRate, bugCount, critical, high, medium, low
        );
    }

    @Transactional(readOnly = true)
    public List<DashboardDtos.TrendPoint> trends(String userEmail) {
        List<UUID> analysisIds = resolveAnalysisIds(userEmail);
        if (analysisIds.isEmpty()) return List.of();

        List<Report> recent = reports.findTop20ByFormatAndAnalysisIdInOrderByGeneratedAtDesc(
                ReportFormat.JSON, analysisIds);
        if (recent.isEmpty()) return List.of();

        // Batch-load analyses and projects to avoid N+1
        Set<UUID> reportAnalysisIds = recent.stream().map(Report::getAnalysisId).collect(Collectors.toSet());
        Map<UUID, Analysis> analysisMap = analyses.findAllById(reportAnalysisIds)
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
        Collections.reverse(result);
        return result;
    }

    private List<UUID> resolveAnalysisIds(String userEmail) {
        return users.findByEmail(userEmail)
                .map(user -> {
                    List<UUID> projectIds = projects.findByOwnerId(user.getId()).stream()
                            .map(Project::getId).toList();
                    if (projectIds.isEmpty()) return List.<UUID>of();
                    return analyses.findByProjectIdIn(projectIds).stream()
                            .map(Analysis::getId).toList();
                })
                .orElse(List.of());
    }
}