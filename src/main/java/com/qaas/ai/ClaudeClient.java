package com.qaas.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";

    @Value("${qaas.claude.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ClaudeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String complete(String prompt) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("Claude API key not configured");

        String body = objectMapper.writeValueAsString(Map.of(
                "model", MODEL,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(45))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Claude API error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Claude API returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("content").get(0).path("text").asText();
    }
}