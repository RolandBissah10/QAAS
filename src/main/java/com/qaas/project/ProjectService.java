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
        p.setOwnerId(owner.getId());
        return ProjectResponse.from(projects.save(p));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return projects.findAll().stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Project get(UUID id) {
        return projects.findById(id).orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Transactional(readOnly = true)
    public ProjectResponse find(UUID id) {
        return ProjectResponse.from(get(id));
    }

    @Transactional
    public ProjectResponse update(UUID id, ProjectRequest request) {
        Project project = get(id);
        project.setName(request.name());
        project.setDescription(request.description());
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID id) {
        Project project = get(id);
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
}
