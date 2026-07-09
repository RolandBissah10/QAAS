package com.qaas.auth;

import com.qaas.auth.AuthDtos.AuthResponse;
import com.qaas.auth.AuthDtos.LoginRequest;
import com.qaas.auth.AuthDtos.RefreshRequest;
import com.qaas.auth.AuthDtos.RegisterRequest;
import com.qaas.auth.entity.RefreshToken;
import com.qaas.auth.repository.RefreshTokenRepository;
import com.qaas.config.AppProperties;
import com.qaas.exception.BadRequestException;
import com.qaas.security.JwtService;
import com.qaas.user.Role;
import com.qaas.user.User;
import com.qaas.user.UserDto;
import com.qaas.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, AppProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new BadRequestException("Email is already registered");
        }
        Role role = users.count() == 0 ? Role.OWNER : Role.VIEWER;
        User user = users.save(new User(request.email(), passwordEncoder.encode(request.password()), role));
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadRequestException("Invalid credentials");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokens.findByToken(request.refreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (!token.isValid()) {
            throw new BadRequestException("Refresh token expired or revoked");
        }
        token.revoke();
        return issueTokens(token.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByToken(refreshToken).ifPresent(RefreshToken::revoke);
    }

    private AuthResponse issueTokens(User user) {
        String refreshToken = UUID.randomUUID().toString();
        refreshTokens.save(new RefreshToken(
                user,
                refreshToken,
                Instant.now().plusSeconds(properties.jwt().refreshTokenDays() * 24L * 60 * 60)
        ));
        return new AuthResponse(jwtService.createAccessToken(user), refreshToken, UserDto.from(user));
    }
}
