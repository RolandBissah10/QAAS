package com.qaas.project.settings;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ProjectSettingsDtos {
    private ProjectSettingsDtos() {}

    public record SettingsResponse(
            UUID id,
            int maxPages,
            String authUrl,
            String authUsername,
            boolean authConfigured,
            String excludedPatterns,
            OffsetDateTime updatedAt
    ) {
        public static SettingsResponse from(ProjectSettings s) {
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
}
