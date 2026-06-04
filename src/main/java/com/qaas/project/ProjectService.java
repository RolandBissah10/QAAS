package com.qaas.project;

import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectDtos.ProjectRequest;
import com.qaas.project.ProjectDtos.ProjectResponse;
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

    public ProjectService(ProjectRepository projects, UserService users) {
        this.projects = projects;
        this.users = users;
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
        projects.delete(get(id));
    }
}
