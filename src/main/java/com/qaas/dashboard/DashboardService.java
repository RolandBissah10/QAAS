package com.qaas.dashboard;

import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.bug.BugRepository;
import com.qaas.bug.Severity;
import com.qaas.execution.ExecutionStatus;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.page.repository.PageRepository;
import com.qaas.project.entity.Project;
import com.qaas.project.entity.ProjectMember;
import com.qaas.project.repository.ProjectMemberRepository;
import com.qaas.project.repository.ProjectRepository;
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
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserRepository users;

    public DashboardService(AnalysisRepository analyses, PageRepository pages,
                            TestExecutionRepository executions, BugRepository bugs,
                            ProjectRepository projects,
                            ProjectMemberRepository members, UserRepository users) {
        this.analyses = analyses;
        this.pages = pages;
        this.executions = executions;
        this.bugs = bugs;
        this.projects = projects;
        this.members = members;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.SummaryResponse summary(String userEmail, UUID projectId) {
        List<UUID> analysisIds = resolveAnalysisIds(userEmail, projectId);
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
    public List<DashboardDtos.TrendPoint> trends(String userEmail, UUID projectId) {
        List<UUID> allIds = resolveAnalysisIds(userEmail, projectId);
        if (allIds.isEmpty()) return List.of();

        // Most recent 20 completed analyses — no report generation required
        List<Analysis> recent = analyses.findTop20ByIdInAndStatusOrderByStartedAtDesc(allIds, "COMPLETED");
        if (recent.isEmpty()) return List.of();

        List<UUID> recentIds = recent.stream().map(Analysis::getId).toList();

        // Batch-load per-analysis counts (3 queries total)
        Map<UUID, Long> pageCounts = toCountMap(pages.countGroupByAnalysisId(recentIds));
        Map<UUID, Long> bugCounts  = toCountMap(bugs.countGroupByAnalysisId(recentIds));
        Map<UUID, Map<ExecutionStatus, Long>> execCounts =
                toStatusCountMap(executions.countByStatusGroupByAnalysisId(recentIds));

        Set<UUID> projectIds = recent.stream()
                .map(Analysis::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> projectNames = projects.findAllById(projectIds)
                .stream().collect(Collectors.toMap(Project::getId, Project::getName));

        return recent.stream().map(a -> {
            UUID aid = a.getId();
            Map<ExecutionStatus, Long> statusMap = execCounts.getOrDefault(aid, Map.of());
            long p   = statusMap.getOrDefault(ExecutionStatus.PASSED, 0L);
            long f   = statusMap.getOrDefault(ExecutionStatus.FAILED, 0L)
                     + statusMap.getOrDefault(ExecutionStatus.ERROR,  0L);
            long tot = p + f;
            int quality = tot == 0 ? 0 : (int) Math.round(p * 100.0 / tot);
            String pName = a.getProjectId() != null
                    ? projectNames.getOrDefault(a.getProjectId(), "Unknown") : "Unknown";
            return new DashboardDtos.TrendPoint(
                    aid.toString(), a.getUrl(),
                    a.getProjectId() != null ? a.getProjectId().toString() : null,
                    pName,
                    DATE_FMT.format(a.getStartedAt()),
                    quality, (int) p, (int) f,
                    bugCounts.getOrDefault(aid, 0L).intValue(),
                    pageCounts.getOrDefault(aid, 0L).intValue()
            );
        }).toList();
    }

    private Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put((UUID) row[0], (Long) row[1]);
        return map;
    }

    private Map<UUID, Map<ExecutionStatus, Long>> toStatusCountMap(List<Object[]> rows) {
        Map<UUID, Map<ExecutionStatus, Long>> map = new HashMap<>();
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            ExecutionStatus status = (ExecutionStatus) row[1];
            Long count = (Long) row[2];
            map.computeIfAbsent(id, k -> new HashMap<>()).put(status, count);
        }
        return map;
    }

    private List<UUID> resolveAnalysisIds(String userEmail, UUID projectId) {
        return users.findByEmail(userEmail)
                .map(user -> {
                    Set<UUID> projectIds;
                    if (projectId != null) {
                        boolean hasAccess = projects.findById(projectId)
                                .map(p -> p.getOwnerId().equals(user.getId()) ||
                                          members.findByProjectIdAndUserId(projectId, user.getId()).isPresent())
                                .orElse(false);
                        if (!hasAccess) return List.<UUID>of();
                        projectIds = Set.of(projectId);
                    } else {
                        projectIds = new HashSet<>();
                        projects.findByOwnerId(user.getId()).stream()
                                .map(Project::getId).forEach(projectIds::add);
                        members.findByUserId(user.getId()).stream()
                                .map(ProjectMember::getProjectId).forEach(projectIds::add);
                    }
                    if (projectIds.isEmpty()) return List.<UUID>of();
                    return analyses.findByProjectIdIn(projectIds).stream()
                            .map(Analysis::getId).toList();
                })
                .orElse(List.of());
    }
}