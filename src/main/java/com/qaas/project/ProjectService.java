package com.qaas.project;

import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectDtos.ProjectRequest;
import com.qaas.project.ProjectDtos.ProjectResponse;
import com.qaas.user.User;
import com.qaas.user.UserService;
import com.qaas.test.ApiTest;
import com.qaas.test.ApiTestRepository;
import com.qaas.execution.TestExecution;
import com.qaas.execution.TestExecutionRepository;
import com.qaas.result.TestResultRepository;
import com.qaas.collection.TestCollection;
import com.qaas.collection.TestCollectionRepository;
import com.qaas.environment.EnvironmentConfig;
import com.qaas.environment.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private final ProjectRepository projects;
    private final UserService users;
    private final ApiTestRepository apiTests;
    private final TestExecutionRepository executions;
    private final TestResultRepository results;
    private final TestCollectionRepository collections;
    private final EnvironmentRepository environments;
    private final EntityManager em;

    public ProjectService(ProjectRepository projects, UserService users, ApiTestRepository apiTests, TestExecutionRepository executions, TestResultRepository results, TestCollectionRepository collections, EnvironmentRepository environments, EntityManager em) {
        this.projects = projects;
        this.users = users;
        this.apiTests = apiTests;
        this.executions = executions;
        this.results = results;
        this.collections = collections;
        this.environments = environments;
        this.em = em;
    }

    @Transactional
    public ProjectResponse create(String ownerEmail, ProjectRequest request) {
        User owner = users.currentUser(ownerEmail);
        return ProjectResponse.from(projects.save(new Project(request.name(), request.description(), owner)));
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
        project.update(request.name(), request.description());
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID id) {
        try {
            // delete results -> executions -> collections (join entries removed) -> tests -> environments -> project
            results.deleteByProjectId(id);
            em.flush();

            executions.deleteByProjectId(id);
            em.flush();

            // delete collections first so join table entries are removed
            collections.deleteByProjectId(id);
            em.flush();

            // then delete tests
            apiTests.deleteByProjectId(id);
            em.flush();

            // delete environments
            environments.deleteByProjectId(id);
            em.flush();

            Project project = get(id);
            projects.delete(project);
        } catch (Exception e) {
            log.error("Failed to delete project {}", id, e);
            throw e;
        }
    }
}
