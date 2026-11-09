package com.qaas.security;

import com.qaas.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @Test
    void rejectsMissingOrShortSigningSecretsAtStartup() {
        assertThatThrownBy(() -> new JwtService(properties("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qaas.jwt.secret");
        assertThatThrownBy(() -> new JwtService(properties("short-secret")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 UTF-8 bytes");
    }

    private AppProperties properties(String jwtSecret) {
        return new AppProperties(
                new AppProperties.Jwt(jwtSecret, 30, 14),
                new AppProperties.Cors(java.util.List.of("http://localhost:5173")),
                new AppProperties.Mail(false, "noreply@qaas.local"),
                new AppProperties.Crypto("crypto-secret-at-least-32-characters-long!!"));
    }
}
