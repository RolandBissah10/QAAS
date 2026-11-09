package com.qaas.environment.controller;

import com.qaas.environment.EnvironmentDtos.EnvironmentRequest;
import com.qaas.environment.EnvironmentDtos.EnvironmentResponse;
import com.qaas.environment.service.TestEnvironmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/environments")
public class TestEnvironmentController {
    private final TestEnvironmentService environments;

    public TestEnvironmentController(TestEnvironmentService environments) {
        this.environments = environments;
    }

    @GetMapping
    List<EnvironmentResponse> list(@PathVariable UUID projectId, Authentication auth) {
        return environments.list(projectId, auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EnvironmentResponse create(
            @PathVariable UUID projectId,
            @Valid @RequestBody EnvironmentRequest request,
            Authentication auth) {
        return environments.create(projectId, auth.getName(), request);
    }

    @PutMapping("/{environmentId}")
    EnvironmentResponse update(
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @Valid @RequestBody EnvironmentRequest request,
            Authentication auth) {
        return environments.update(projectId, environmentId, auth.getName(), request);
    }

    @DeleteMapping("/{environmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            Authentication auth) {
        environments.delete(projectId, environmentId, auth.getName());
    }
}
