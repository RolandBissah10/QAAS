package com.qaas.environment;

import com.qaas.environment.EnvironmentDtos.EnvironmentRequest;
import com.qaas.environment.EnvironmentDtos.EnvironmentResponse;
import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EnvironmentService {
    private final EnvironmentRepository environments;
    private final ProjectService projects;

    public EnvironmentService(EnvironmentRepository environments, ProjectService projects) {
        this.environments = environments;
        this.projects = projects;
    }

    @Transactional
    public EnvironmentResponse create(EnvironmentRequest request) {
        return EnvironmentResponse.from(environments.save(new EnvironmentConfig(projects.get(request.projectId()), request.name(), request.baseUrl())));
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> list() {
        return environments.findAll().stream().map(EnvironmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EnvironmentConfig get(UUID id) {
        return environments.findById(id).orElseThrow(() -> new NotFoundException("Environment not found"));
    }

    @Transactional
    public EnvironmentResponse update(UUID id, EnvironmentRequest request) {
        EnvironmentConfig environment = get(id);
        environment.update(request.name(), request.baseUrl());
        return EnvironmentResponse.from(environment);
    }

    @Transactional
    public void delete(UUID id) {
        environments.delete(get(id));
    }
}
