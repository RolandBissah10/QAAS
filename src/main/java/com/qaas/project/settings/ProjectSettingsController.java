package com.qaas.project.settings;

import com.qaas.exception.NotFoundException;
import com.qaas.project.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/settings")
public class ProjectSettingsController {

    private final ProjectSettingsRepository repository;
    private final ProjectRepository projectRepository;

    public ProjectSettingsController(ProjectSettingsRepository repository,
                                     ProjectRepository projectRepository) {
        this.repository = repository;
        this.projectRepository = projectRepository;
    }

    public record SettingsResponse(
            UUID id,
            int maxPages,
            String authUrl,
            String authUsername,
            boolean authConfigured,
            String excludedPatterns,
            OffsetDateTime updatedAt
    ) {
        static SettingsResponse from(ProjectSettings s) {
            return new SettingsResponse(
                    s.getId(),
                    s.getMaxPages(),
                    s.getAuthUrl(),
                    s.getAuthUsername(),
                    s.getAuthPassword() != null && !s.getAuthPassword().isBlank(),
                    s.getExcludedPatterns(),
                    s.getUpdatedAt()
            );
        }
    }

    public record SettingsRequest(
            int maxPages,
            String authUrl,
            String authUsername,
            String authPassword,    // blank = keep existing password
            String excludedPatterns
    ) {}

    @GetMapping
    public ResponseEntity<SettingsResponse> get(@PathVariable UUID projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        return repository.findByProjectId(projectId)
                .map(s -> ResponseEntity.ok(SettingsResponse.from(s)))
                .orElseGet(() -> {
                    // Return sensible defaults without persisting
                    ProjectSettings defaults = new ProjectSettings();
                    defaults.setProjectId(projectId);
                    return ResponseEntity.ok(SettingsResponse.from(defaults));
                });
    }

    @PutMapping
    public SettingsResponse save(@PathVariable UUID projectId,
                                 @RequestBody SettingsRequest req) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        ProjectSettings settings = repository.findByProjectId(projectId)
                .orElseGet(() -> {
                    ProjectSettings s = new ProjectSettings();
                    s.setProjectId(projectId);
                    return s;
                });

        settings.setMaxPages(req.maxPages() > 0 ? req.maxPages() : 20);
        settings.setAuthUrl(req.authUrl());
        settings.setAuthUsername(req.authUsername());

        // Only update password if a new one is provided
        if (req.authPassword() != null && !req.authPassword().isBlank()) {
            settings.setAuthPassword(req.authPassword());
        }

        settings.setExcludedPatterns(req.excludedPatterns());
        settings.setUpdatedAt(OffsetDateTime.now());

        return SettingsResponse.from(repository.save(settings));
    }
}