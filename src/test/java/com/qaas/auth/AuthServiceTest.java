package com.qaas.auth;

import com.qaas.auth.AuthDtos.RegisterRequest;
import com.qaas.auth.repository.RefreshTokenRepository;
import com.qaas.config.AppProperties;
import com.qaas.exception.BadRequestException;
import com.qaas.security.JwtService;
import com.qaas.user.Role;
import com.qaas.user.User;
import com.qaas.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserRepository users;

    @Mock
    RefreshTokenRepository refreshTokens;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    AppProperties properties;

    @InjectMocks
    AuthService service;

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("owner@qaas.dev")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("owner@qaas.dev", "password123");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email is already registered");
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = new User("owner@qaas.dev", "encoded", Role.OWNER);
        when(users.findByEmail("owner@qaas.dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("owner@qaas.dev", "wrong-password")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid credentials");
    }
}
