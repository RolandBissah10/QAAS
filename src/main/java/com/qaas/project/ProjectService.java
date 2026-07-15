package com.qaas.project;

import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.bug.BugRepository;
import com.qaas.exception.NotFoundException;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.page.repository.PageRepository;
import com.qaas.project.ProjectDtos.ProjectRequest;
import com.qaas.project.ProjectDtos.ProjectResponse;
import com.qaas.project.entity.Project;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.report.ReportRepository;
import com.qaas.user.User;
import com.qaas.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final UserService users;
    private final AnalysisRepository analyses;
    private final PageRepository pages;
    private final GeneratedTestRepository generatedTests;
    private final TestExecutionRepository executions;
    private final BugRepository bugs;
    private final ReportRepository reports;

    public ProjectService(ProjectRepository projects, UserService users, AnalysisRepository analyses,
                          PageRepository pages, GeneratedTestRepository generatedTests,
                          TestExecutionRepository executions, BugRepository bugs, ReportRepository reports) {
        this.projects = projects;
        this.users = users;
        this.analyses = analyses;
        this.pages = pages;
        this.generatedTests = generatedTests;
        this.executions = executions;
        this.bugs = bugs;
        this.reports = reports;
    }

    @Transactional
    public ProjectResponse create(String ownerEmail, ProjectRequest request) {
        User owner = users.currentUser(ownerEmail);
        Project p = new Project();
        p.setName(request.name());
        p.setDescription(request.description());
        p.setBaseUrl(request.baseUrl());
        p.setOwnerId(owner.getId());
        return ProjectResponse.from(projects.save(p));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(String ownerEmail) {
        User owner = users.currentUser(ownerEmail);
        return projects.findByOwnerId(owner.getId()).stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse find(UUID id, String ownerEmail) {
        return ProjectResponse.from(getOwned(id, ownerEmail));
    }

    @Transactional
    public ProjectResponse update(UUID id, String ownerEmail, ProjectRequest request) {
        Project project = getOwned(id, ownerEmail);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setBaseUrl(request.baseUrl());
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID id, String ownerEmail) {
        Project project = getOwned(id, ownerEmail);
        analyses.findByProjectId(id).forEach(analysis -> {
            UUID analysisId = analysis.getId();
            bugs.deleteAll(bugs.findByAnalysisId(analysisId));
            reports.deleteAll(reports.findByAnalysisId(analysisId));
            executions.deleteAll(executions.findByAnalysisId(analysisId));
            pages.findByAnalysisId(analysisId).forEach(page ->
                generatedTests.deleteAll(generatedTests.findByPageId(page.getId()))
            );
            pages.deleteAll(pages.findByAnalysisId(analysisId));
        });
        analyses.deleteAll(analyses.findByProjectId(id));
        projects.delete(project);
    }

    // Used internally by AnalysisController to verify project ownership
    public Project getOwned(UUID id, String ownerEmail) {
        User owner = users.currentUser(ownerEmail);
        Project project = projects.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (!owner.getId().equals(project.getOwnerId())) {
            throw new NotFoundException("Project not found");
        }
        return project;
    }

    // Throws NotFoundException if the authenticated user does not own the project
    // that contains this analysis. Used by all sub-entity controllers.
    @Transactional(readOnly = true)
    public void verifyAnalysisAccess(UUID analysisId, String ownerEmail) {
        var analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new NotFoundException("Not found"));
        if (analysis.getProjectId() != null) {
            getOwned(analysis.getProjectId(), ownerEmail);
        }
    }
}