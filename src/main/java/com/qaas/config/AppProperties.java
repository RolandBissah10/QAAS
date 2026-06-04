package com.qaas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "qaas")
public record AppProperties(Jwt jwt, Cors cors) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
