package com.qaas.project.settings;

import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectService;
import com.qaas.project.entity.Project;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsRequest;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Project settings are scoped to a single project: access is decided by the caller's
 * membership of <em>that</em> project, never by their global role. Reads are open to the
 * owner and any member; writes are restricted to the project owner, since the settings
 * hold crawl credentials.
 *
 * <p>Denial is reported as {@link NotFoundException} (404) rather than 403, matching
 * {@link ProjectService} — a caller must not be able to probe for projects they cannot see.
 */
@Service
public class ProjectSettingsService {

    private static final int DEFAULT_MAX_PAGES = 20;

    private final ProjectSettingsRepository repository;
    private final ProjectRepository projects;
    private final ProjectService projectService;

    public ProjectSettingsService(ProjectSettingsRepository repository,
                                  ProjectRepository projects,
                                  ProjectService projectService) {
        this.repository = repository;
        this.projects = projects;
        this.projectService = projectService;
    }

    /** Readable by the project owner and by any VIEWER/TESTER member of that project. */
    @Transactional(readOnly = true)
    public SettingsResponse get(UUID projectId, String userEmail) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (!projectService.hasAccess(project, userEmail)) {
            throw new NotFoundException("Project not found");
        }

        return repository.findByProjectId(projectId)
                .map(SettingsResponse::from)
                .orElseGet(() -> {
                    // Return sensible defaults without persisting
                    ProjectSettings defaults = new ProjectSettings();
                    defaults.setProjectId(projectId);
                    return SettingsResponse.from(defaults);
                });
    }

    /** Writable only by the project owner — members, whatever their role, cannot change settings. */
    @Transactional
    public SettingsResponse save(UUID projectId, String userEmail, SettingsRequest req) {
        projectService.getOwned(projectId, userEmail);   // owner-only; throws 404 otherwise

        ProjectSettings settings = repository.findByProjectId(projectId)
                .orElseGet(() -> {
                    ProjectSettings s = new ProjectSettings();
                    s.setProjectId(projectId);
                    return s;
                });

        settings.setMaxPages(req.maxPages() > 0 ? req.maxPages() : DEFAULT_MAX_PAGES);
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
