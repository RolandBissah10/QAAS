package com.qaas.environment;

import com.qaas.environment.entity.TestEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class EnvironmentDtos {
    private EnvironmentDtos() {
    }

    public record EnvironmentRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 2048) String baseUrl,
            @Size(max = 255) String authUsername,
            @Size(max = 1024) String authPassword,
            boolean clearCredentials
    ) {
    }

    public record EnvironmentResponse(
            UUID id,
            UUID projectId,
            String name,
            String baseUrl,
            String authUsername,
            boolean credentialsConfigured,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static EnvironmentResponse from(TestEnvironment environment) {
            return new EnvironmentResponse(environment.getId(), environment.getProjectId(),
                    environment.getName(), environment.getBaseUrl(), environment.getAuthUsername(),
                    environment.hasCredentials(), environment.getCreatedAt(), environment.getUpdatedAt());
        }
    }

    public record RunEnvironment(
            UUID id,
            String name,
            String baseUrl,
            String authUsername,
            String authPassword
    ) {
        public static RunEnvironment from(TestEnvironment environment) {
            return new RunEnvironment(environment.getId(), environment.getName(), environment.getBaseUrl(),
                    environment.getAuthUsername(), environment.getAuthPassword());
        }
    }
}
