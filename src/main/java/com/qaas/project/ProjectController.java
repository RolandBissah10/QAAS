package com.qaas.project;

import com.qaas.project.ProjectDtos.ProjectRequest;
import com.qaas.project.ProjectDtos.ProjectResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProjectResponse create(Authentication auth, @Valid @RequestBody ProjectRequest request) {
        return service.create(auth.getName(), request);
    }

    @GetMapping
    List<ProjectResponse> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @GetMapping("/{id}")
    ProjectResponse get(@PathVariable UUID id, Authentication auth) {
        return service.find(id, auth.getName());
    }

    @PutMapping("/{id}")
    ProjectResponse update(@PathVariable UUID id, Authentication auth, @Valid @RequestBody ProjectRequest request) {
        return service.update(id, auth.getName(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, Authentication auth) {
        service.delete(id, auth.getName());
    }
}