package com.qaas.analysis.service;

import com.qaas.analysis.AnalysisCancellationRegistry;
import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.project.entity.Project;
import com.qaas.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ScheduledAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAnalysisService.class);

    private final ProjectRepository projects;
    private final AnalysisRepository analyses;
    private final AnalysisCancellationRegistry cancellationRegistry;
    private final AnalysisPipelineService pipelineService;

    public ScheduledAnalysisService(ProjectRepository projects, AnalysisRepository analyses,
                                    AnalysisCancellationRegistry cancellationRegistry,
                                    AnalysisPipelineService pipelineService) {
        this.projects = projects;
        this.analyses = analyses;
        this.cancellationRegistry = cancellationRegistry;
        this.pipelineService = pipelineService;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void checkDueProjects() {
        List<Project> scheduled = projects.findByScheduleEnabledTrue();
        if (scheduled.isEmpty()) return;

        OffsetDateTime now = OffsetDateTime.now();
        for (Project project : scheduled) {
            if (project.getBaseUrl() == null || project.getBaseUrl().isBlank()) continue;
            if (!isDue(project, now)) continue;
            if (analyses.existsByProjectIdAndStatus(project.getId(), "RUNNING")) continue;

            triggerAnalysis(project, now);
        }
    }

    private boolean isDue(Project project, OffsetDateTime now) {
        String expr = project.getScheduleExpression();
        if (expr == null || expr.isBlank()) return false;
        try {
            // Spring CronExpression uses 6 fields — prepend "0" for the seconds field
            CronExpression cron = CronExpression.parse("0 " + expr);
            OffsetDateTime lastRun = project.getLastScheduledAt();
            LocalDateTime from = lastRun != null
                    ? lastRun.toLocalDateTime()
                    : now.minusDays(1).toLocalDateTime();
            @SuppressWarnings("DataFlowIssue")
            LocalDateTime next = cron.next(from);
            return next != null && !next.isAfter(now.toLocalDateTime());
        } catch (Exception e) {
            log.warn("Invalid schedule expression for project {}: '{}'", project.getId(), expr);
            return false;
        }
    }

    private void triggerAnalysis(Project project, OffsetDateTime now) {
        try {
            Analysis analysis = new Analysis();
            analysis.setProjectId(project.getId());
            analysis.setUrl(project.getBaseUrl());
            analysis.setStatus("RUNNING");
            analysis.setStartedAt(now);
            analysis.setTriggeredByUserId(project.getOwnerId());
            analyses.save(analysis);

            project.setLastScheduledAt(now);
            projects.save(project);

            cancellationRegistry.register(analysis.getId());
            pipelineService.run(analysis.getId(), project.getBaseUrl(), false);

            log.info("Scheduled analysis {} started for project {}", analysis.getId(), project.getId());
        } catch (Exception e) {
            log.error("Failed to trigger scheduled analysis for project {}: {}", project.getId(), e.getMessage());
        }
    }
}