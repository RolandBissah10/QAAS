package com.qaas.environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class EnvironmentDtos {
    private EnvironmentDtos() {
    }

    public record EnvironmentRequest(@NotNull UUID projectId, @NotBlank String name, @NotBlank String baseUrl) {
    }

    public record EnvironmentResponse(UUID id, UUID projectId, String name, String baseUrl) {
        public static EnvironmentResponse from(EnvironmentConfig environment) {
            return new EnvironmentResponse(environment.getId(), environment.getProject().getId(), environment.getName(), environment.getBaseUrl());
        }
    }
}
