package com.qaas.project;

import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.bug.BugRepository;
import com.qaas.exception.NotFoundException;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.page.repository.PageRepository;
import com.qaas.project.ProjectDtos.MemberRequest;
import com.qaas.project.ProjectDtos.MemberResponse;
import com.qaas.project.ProjectDtos.ProjectRequest;
import com.qaas.project.ProjectDtos.ProjectResponse;
import com.qaas.project.entity.Project;
import com.qaas.project.entity.ProjectMember;
import com.qaas.project.repository.ProjectMemberRepository;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.report.ReportRepository;
import com.qaas.user.User;
import com.qaas.user.UserRepository;
import com.qaas.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserService userService;
    private final UserRepository userRepository;
    private final AnalysisRepository analyses;
    private final PageRepository pages;
    private final GeneratedTestRepository generatedTests;
    private final TestExecutionRepository executions;
    private final BugRepository bugs;
    private final ReportRepository reports;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members,
                          UserService userService, UserRepository userRepository,
                          AnalysisRepository analyses, PageRepository pages,
                          GeneratedTestRepository generatedTests, TestExecutionRepository executions,
                          BugRepository bugs, ReportRepository reports) {
        this.projects = projects;
        this.members = members;
        this.userService = userService;
        this.userRepository = userRepository;
        this.analyses = analyses;
        this.pages = pages;
        this.generatedTests = generatedTests;
        this.executions = executions;
        this.bugs = bugs;
        this.reports = reports;
    }

    // ── CRUD ────────────────────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse create(String ownerEmail, ProjectRequest request) {
        User owner = userService.currentUser(ownerEmail);
        Project p = new Project();
        p.setName(request.name());
        p.setDescription(request.description());
        p.setBaseUrl(request.baseUrl());
        p.setOwnerId(owner.getId());
        applySchedule(p, request);
        return ProjectResponse.from(projects.save(p));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(String userEmail) {
        User user = userService.currentUser(userEmail);
        List<ProjectResponse> result = new ArrayList<>();

        // Owned projects
        projects.findByOwnerId(user.getId())
                .forEach(p -> result.add(ProjectResponse.from(p)));

        // Shared projects (member of)
        members.findByUserId(user.getId()).forEach(m -> {
            projects.findById(m.getProjectId()).ifPresent(p ->
                    result.add(ProjectResponse.fromMember(p, m.getMemberRole())));
        });

        return result;
    }

    @Transactional(readOnly = true)
    public ProjectResponse find(UUID id, String userEmail) {
        User user = userService.currentUser(userEmail);
        Project project = projects.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        if (user.getId().equals(project.getOwnerId())) {
            return ProjectResponse.from(project);
        }
        return members.findByProjectIdAndUserId(id, user.getId())
                .map(m -> ProjectResponse.fromMember(project, m.getMemberRole()))
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Transactional
    public ProjectResponse update(UUID id, String ownerEmail, ProjectRequest request) {
        Project project = getOwned(id, ownerEmail);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setBaseUrl(request.baseUrl());
        applySchedule(project, request);
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID id, String ownerEmail) {
        Project project = getOwned(id, ownerEmail);
        members.deleteByProjectId(id);
        analyses.findByProjectId(id).forEach(analysis -> {
            UUID analysisId = analysis.getId();
            bugs.deleteAll(bugs.findByAnalysisId(analysisId));
            reports.deleteAll(reports.findByAnalysisId(analysisId));
            executions.deleteAll(executions.findByAnalysisId(analysisId));
            pages.findByAnalysisId(analysisId).forEach(page ->
                    generatedTests.deleteAll(generatedTests.findByPageId(page.getId())));
            pages.deleteAll(pages.findByAnalysisId(analysisId));
        });
        analyses.deleteAll(analyses.findByProjectId(id));
        projects.delete(project);
    }

    // ── Member management ────────────────────────────────────────────────────────

    @Transactional
    public MemberResponse addMember(UUID projectId, String ownerEmail, MemberRequest request) {
        getOwned(projectId, ownerEmail);  // only owner can invite

        String role = request.role().toUpperCase();
        if (!role.equals("VIEWER") && !role.equals("TESTER")) {
            throw new IllegalArgumentException("Role must be VIEWER or TESTER");
        }

        User target = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.email()));

        // Idempotent: update role if already a member
        ProjectMember member = members.findByProjectIdAndUserId(projectId, target.getId())
                .orElseGet(() -> {
                    ProjectMember m = new ProjectMember();
                    m.setProjectId(projectId);
                    m.setUserId(target.getId());
                    return m;
                });
        member.setMemberRole(role);
        members.save(member);

        return toMemberResponse(member, target);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID projectId, String ownerEmail) {
        getOwned(projectId, ownerEmail);
        return members.findByProjectId(projectId).stream()
                .map(m -> userRepository.findById(m.getUserId())
                        .map(u -> toMemberResponse(m, u))
                        .orElse(null))
                .filter(r -> r != null)
                .toList();
    }

    @Transactional
    public void removeMember(UUID projectId, String ownerEmail, UUID userId) {
        getOwned(projectId, ownerEmail);
        members.deleteByProjectIdAndUserId(projectId, userId);
    }

    // ── Access checks (used by AnalysisController and sub-entity controllers) ───

    /** True if the user owns the project or is any kind of member. */
    public boolean hasAccess(Project project, String userEmail) {
        User user = userService.currentUser(userEmail);
        if (user.getId().equals(project.getOwnerId())) return true;
        return members.findByProjectIdAndUserId(project.getId(), user.getId()).isPresent();
    }

    /** True if the user owns the project or is a TESTER member. */
    public boolean hasTesterAccess(Project project, String userEmail) {
        User user = userService.currentUser(userEmail);
        if (user.getId().equals(project.getOwnerId())) return true;
        return members.findByProjectIdAndUserId(project.getId(), user.getId())
                .map(m -> "TESTER".equals(m.getMemberRole()))
                .orElse(false);
    }

    // Used internally by AnalysisController to verify project ownership (edit operations)
    public Project getOwned(UUID id, String ownerEmail) {
        User owner = userService.currentUser(ownerEmail);
        Project project = projects.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (!owner.getId().equals(project.getOwnerId())) {
            throw new NotFoundException("Project not found");
        }
        return project;
    }

    // Throws NotFoundException if the authenticated user does not have any access to
    // the project that contains this analysis. Used by all sub-entity controllers.
    @Transactional(readOnly = true)
    public void verifyAnalysisAccess(UUID analysisId, String userEmail) {
        var analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new NotFoundException("Not found"));
        if (analysis.getProjectId() == null) return;

        Project project = projects.findById(analysis.getProjectId())
                .orElseThrow(() -> new NotFoundException("Not found"));
        if (!hasAccess(project, userEmail)) {
            throw new NotFoundException("Not found");
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────────

    private void applySchedule(Project project, ProjectRequest request) {
        if (request.scheduleExpression() != null) {
            project.setScheduleExpression(request.scheduleExpression().isBlank()
                    ? null : request.scheduleExpression().trim());
        }
        if (request.scheduleEnabled() != null) {
            project.setScheduleEnabled(request.scheduleEnabled());
        }
    }

    private MemberResponse toMemberResponse(ProjectMember m, User u) {
        return new MemberResponse(u.getId(), u.getEmail(), u.getDisplayName(), m.getMemberRole(), m.getJoinedAt());
    }
}