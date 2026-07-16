package com.qaas.project;

import com.qaas.project.entity.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record ProjectRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 2048) String baseUrl,
            String scheduleExpression,
            Boolean scheduleEnabled
    ) {}

    public record ProjectResponse(
            UUID id, String name, String description, String baseUrl,
            UUID ownerId, OffsetDateTime createdAt,
            String scheduleExpression, boolean scheduleEnabled,
            boolean owner, String memberRole
    ) {
        public static ProjectResponse from(Project project) {
            return new ProjectResponse(
                    project.getId(), project.getName(), project.getDescription(), project.getBaseUrl(),
                    project.getOwnerId(), project.getCreatedAt(),
                    project.getScheduleExpression(), project.isScheduleEnabled(),
                    true, null);
        }

        public static ProjectResponse fromMember(Project project, String memberRole) {
            return new ProjectResponse(
                    project.getId(), project.getName(), project.getDescription(), project.getBaseUrl(),
                    project.getOwnerId(), project.getCreatedAt(),
                    project.getScheduleExpression(), project.isScheduleEnabled(),
                    false, memberRole);
        }
    }

    public record MemberRequest(String email, @NotBlank String role) {}

    public record MemberResponse(UUID userId, String email, String displayName, String role, OffsetDateTime joinedAt) {}
}