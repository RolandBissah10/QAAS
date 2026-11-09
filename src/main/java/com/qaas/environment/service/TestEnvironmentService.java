package com.qaas.environment.service;

import com.qaas.environment.EnvironmentDtos.EnvironmentRequest;
import com.qaas.environment.EnvironmentDtos.EnvironmentResponse;
import com.qaas.environment.EnvironmentDtos.RunEnvironment;
import com.qaas.environment.entity.TestEnvironment;
import com.qaas.environment.repository.TestEnvironmentRepository;
import com.qaas.exception.BadRequestException;
import com.qaas.exception.ConflictException;
import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectService;
import com.qaas.security.PublicHttpUrlValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
public class TestEnvironmentService {
    private final TestEnvironmentRepository environments;
    private final ProjectService projects;

    public TestEnvironmentService(TestEnvironmentRepository environments, ProjectService projects) {
        this.environments = environments;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> list(UUID projectId, String userEmail) {
        authorizeProject(projectId, userEmail);
        return environments.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(EnvironmentResponse::from)
                .toList();
    }

    @Transactional
    public EnvironmentResponse create(UUID projectId, String userEmail, EnvironmentRequest request) {
        projects.getOwned(projectId, userEmail);
        String name = request.name().trim();
        validateBaseUrl(request.baseUrl());
        validateCredentials(request.authUsername(), request.authPassword(), false, false);
        if (environments.existsByProjectIdAndNameIgnoreCase(projectId, name)) {
            throw new ConflictException("An environment with this name already exists");
        }
        TestEnvironment environment = environments.save(new TestEnvironment(
                projectId, name, normalizeBaseUrl(request.baseUrl()), blankToNull(request.authUsername()),
                blankToNull(request.authPassword())));
        return EnvironmentResponse.from(environment);
    }

    @Transactional
    public EnvironmentResponse update(UUID projectId, UUID environmentId, String userEmail,
                                      EnvironmentRequest request) {
        projects.getOwned(projectId, userEmail);
        TestEnvironment environment = requireEnvironment(projectId, environmentId);
        String name = request.name().trim();
        if (!environment.getName().equalsIgnoreCase(name)
                && environments.existsByProjectIdAndNameIgnoreCase(projectId, name)) {
            throw new ConflictException("An environment with this name already exists");
        }
        validateBaseUrl(request.baseUrl());
        validateCredentials(request.authUsername(), request.authPassword(), request.clearCredentials(),
                environment.hasCredentials());
        environment.update(name, normalizeBaseUrl(request.baseUrl()), blankToNull(request.authUsername()),
                request.authPassword(), request.clearCredentials());
        return EnvironmentResponse.from(environments.save(environment));
    }

    @Transactional
    public void delete(UUID projectId, UUID environmentId, String userEmail) {
        projects.getOwned(projectId, userEmail);
        environments.delete(requireEnvironment(projectId, environmentId));
    }

    @Transactional(readOnly = true)
    public RunEnvironment requireRunEnvironment(UUID projectId, UUID environmentId) {
        return RunEnvironment.from(requireEnvironment(projectId, environmentId));
    }

    @Transactional(readOnly = true)
    public RunEnvironment findRunEnvironment(UUID projectId, UUID environmentId) {
        if (environmentId == null) {
            return null;
        }
        return requireRunEnvironment(projectId, environmentId);
    }

    private void authorizeProject(UUID projectId, String userEmail) {
        projects.find(projectId, userEmail);
    }

    private TestEnvironment requireEnvironment(UUID projectId, UUID environmentId) {
        return environments.findByIdAndProjectId(environmentId, projectId)
                .orElseThrow(() -> new NotFoundException("Environment not found"));
    }

    private void validateBaseUrl(String value) {
        PublicHttpUrlValidator.validate(value);
        URI uri = URI.create(value);
        if (uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))) {
            throw new BadRequestException("Environment URL must be an origin such as https://qa.example.com");
        }
    }

    private String normalizeBaseUrl(String value) {
        URI uri = URI.create(value.trim());
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void validateCredentials(String username, String password, boolean clearCredentials,
                                     boolean alreadyConfigured) {
        if (clearCredentials) {
            return;
        }
        boolean hasUsername = username != null && !username.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        if (hasPassword && !hasUsername) {
            throw new BadRequestException("Provide both a username and password for environment credentials");
        }
        if (hasUsername && !hasPassword && !alreadyConfigured) {
            throw new BadRequestException("Provide both a username and password for environment credentials");
        }
        if (alreadyConfigured && !hasUsername && !hasPassword) {
            throw new BadRequestException("Select Remove saved credentials to clear existing credentials");
        }
    }
}
