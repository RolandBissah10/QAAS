package com.qaas.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record ProjectRequest(@NotBlank @Size(max = 160) String name, @Size(max = 2000) String description) {
    }

    public record ProjectResponse(UUID id, String name, String description, UUID ownerId, Instant createdAt) {
        public static ProjectResponse from(Project project) {
            return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getOwner().getId(), project.getCreatedAt());
        }
    }
}
