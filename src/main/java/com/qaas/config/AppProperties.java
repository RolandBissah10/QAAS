package com.qaas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "qaas")
public record AppProperties(Jwt jwt, Cors cors, Mail mail, Crypto crypto) {
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {
    }

    /** Key for encrypting secrets at rest (currently project crawl credentials). */
    public record Crypto(String secret) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Mail(boolean enabled, String from) {
    }
}