package com.qaas.project.settings;

import com.qaas.project.settings.ProjectSettingsDtos.SettingsRequest;
import com.qaas.project.settings.ProjectSettingsDtos.SettingsResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/settings")
public class ProjectSettingsController {

    private final ProjectSettingsService service;

    public ProjectSettingsController(ProjectSettingsService service) {
        this.service = service;
    }

    @GetMapping
    SettingsResponse get(@PathVariable UUID projectId, Authentication auth) {
        return service.get(projectId, auth.getName());
    }

    @PutMapping
    SettingsResponse save(@PathVariable UUID projectId, Authentication auth,
                          @RequestBody SettingsRequest req) {
        return service.save(projectId, auth.getName(), req);
    }
}
