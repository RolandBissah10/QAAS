package com.qaas.security;

import com.qaas.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void corsUsesConfiguredExactOriginsWithCredentials() {
        var source = (UrlBasedCorsConfigurationSource) new SecurityConfig()
                .corsConfigurationSource(properties(List.of("https://qaas.example.com", "http://localhost:5173")));

        CorsConfiguration configuration = source.getCorsConfigurations().get("/**");

        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://qaas.example.com", "http://localhost:5173");
        assertThat(configuration.getAllowedOriginPatterns()).isNull();
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void corsRejectsWildcardOrigins() {
        assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource(properties(List.of("*"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qaas.cors.allowed-origins");
    }

    private AppProperties properties(List<String> origins) {
        return new AppProperties(
                new AppProperties.Jwt("jwt-secret-at-least-32-characters-long!!", 30, 14),
                new AppProperties.Cors(origins),
                new AppProperties.Mail(false, "noreply@qaas.local"),
                new AppProperties.Crypto("crypto-secret-at-least-32-characters-long!!"));
    }
}
