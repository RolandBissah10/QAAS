package com.qaas.user;

import java.time.Instant;
import java.util.UUID;

public record UserDto(UUID id, String email, Role role, String displayName, Instant createdAt) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getRole(), user.getDisplayName(), user.getCreatedAt());
    }
}
