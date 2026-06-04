package com.qaas.auth;

import com.qaas.user.Role;
import com.qaas.user.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @Size(min = 8, max = 100) String password,
            @NotNull Role role
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record PasswordResetRequest(@Email @NotBlank String email) {
    }

    public record PasswordResetConfirmRequest(@NotBlank String token, @Size(min = 8, max = 100) String newPassword) {
    }

    public record PasswordResetTokenResponse(String resetToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserDto user) {
    }
}
