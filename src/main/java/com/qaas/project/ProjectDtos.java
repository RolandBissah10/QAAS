package com.qaas.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record ProjectRequest(@NotBlank @Size(max = 160) String name, @Size(max = 2000) String description) {
    }

    public record ProjectResponse(UUID id, String name, String description, UUID ownerId, OffsetDateTime createdAt) {
        public static ProjectResponse from(com.qaas.project.entity.Project project) {
            return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getOwnerId(), project.getCreatedAt());
        }
    }
}
