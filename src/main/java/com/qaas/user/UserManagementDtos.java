package com.qaas.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserManagementDtos {
    private UserManagementDtos() {
    }

    public record UpdateUserRequest(@NotNull Role role, @Size(max = 120) String displayName) {
    }
}
