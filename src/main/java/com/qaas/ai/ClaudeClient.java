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

    // Claude
    private static final String CLAUDE_URL   = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_MODEL = "claude-haiku-4-5-20251001";

    // Gemini
    private static final String GEMINI_URL   = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final String GEMINI_MODEL = "gemini-2.0-flash";

    @Value("${qaas.claude.api-key:}")
    private String claudeKey;

    @Value("${qaas.gemini.api-key:}")
    private String geminiKey;

    // "claude" | "gemini" | "auto" (auto picks claude if key present, otherwise gemini)
    @Value("${qaas.ai.provider:auto}")
    private String provider;

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ClaudeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return switch (resolvedProvider()) {
            case "claude" -> claudeKey != null && !claudeKey.isBlank();
            case "gemini" -> geminiKey != null && !geminiKey.isBlank();
            default       -> false;
        };
    }

    public String complete(String prompt) throws Exception {
        return switch (resolvedProvider()) {
            case "claude" -> callClaude(prompt);
            case "gemini" -> callGemini(prompt);
            default -> throw new IllegalStateException("No AI provider configured. Set qaas.claude.api-key or qaas.gemini.api-key.");
        };
    }

    // ── Resolved provider ─────────────────────────────────────────────────────

    private String resolvedProvider() {
        if ("claude".equalsIgnoreCase(provider)) return "claude";
        if ("gemini".equalsIgnoreCase(provider)) return "gemini";
        // auto: prefer Claude, fall back to Gemini
        if (claudeKey != null && !claudeKey.isBlank()) return "claude";
        if (geminiKey != null && !geminiKey.isBlank()) return "gemini";
        return "none";
    }

    // ── Claude ────────────────────────────────────────────────────────────────

    private String callClaude(String prompt) throws Exception {
        if (claudeKey == null || claudeKey.isBlank())
            throw new IllegalStateException("Claude API key not configured");

        String body = objectMapper.writeValueAsString(Map.of(
                "model", CLAUDE_MODEL,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_URL))
                .timeout(Duration.ofSeconds(45))
                .header("x-api-key", claudeKey)
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

    // ── Gemini ────────────────────────────────────────────────────────────────

    private String callGemini(String prompt) throws Exception {
        if (geminiKey == null || geminiKey.isBlank())
            throw new IllegalStateException("Gemini API key not configured");

        String body = objectMapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of("maxOutputTokens", 1024)
        ));

        String url = GEMINI_URL + "?key=" + geminiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("Gemini API error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini API returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("candidates").get(0)
                   .path("content").path("parts").get(0)
                   .path("text").asText();
    }
}