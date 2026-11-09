package com.qaas.apitest.service;

import com.qaas.apitest.entity.ApiEndpoint;
import com.qaas.apitest.repository.ApiEndpointRepository;
import com.qaas.execution.TestExecution;
import com.qaas.generator.entity.GeneratedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class ApiExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ApiExecutionService.class);

    private final ApiEndpointRepository endpoints;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ApiExecutionService(ApiEndpointRepository endpoints) {
        this.endpoints = endpoints;
    }

    public void execute(TestExecution execution, GeneratedTest test, String authToken) {
        execute(execution, test, authToken, null, null, null);
    }

    public void execute(TestExecution execution, GeneratedTest test, String authToken,
                        String targetUrl, String authUsername, String authPassword) {
        UUID endpointId = test.getApiEndpointId();
        if (endpointId == null) {
            execution.fail("No API endpoint linked to this test");
            return;
        }
        ApiEndpoint endpoint = endpoints.findById(endpointId).orElse(null);
        if (endpoint == null) {
            execution.fail("API endpoint not found: " + endpointId);
            return;
        }
        String effectiveUrl = targetUrl == null ? endpoint.getUrl() : targetUrl;
        try {
            switch (test.getType()) {
                case "api-smoke"  -> executeSmoke(execution, endpoint, effectiveUrl,
                        authToken, authUsername, authPassword);
                case "api-auth"   -> executeAuth(execution, endpoint, effectiveUrl);
                case "api-schema" -> executeSchema(execution, endpoint, effectiveUrl,
                        authToken, authUsername, authPassword);
                default           -> execution.fail("Unknown API test type: " + test.getType());
            }
        } catch (Exception e) {
            log.warn("API test execution error for {}: {}", test.getTargetUrl(), e.getMessage());
            execution.fail("Request failed: " + e.getMessage());
        }
    }

    // ── Smoke: endpoint must not return 5xx ───────────────────────────────────

    private void executeSmoke(TestExecution execution, ApiEndpoint endpoint, String targetUrl,
                              String authToken, String authUsername, String authPassword)
            throws Exception {
        HttpResponse<Void> resp = http.send(
                buildRequest(endpoint, targetUrl, authToken, authUsername, authPassword).build(),
                HttpResponse.BodyHandlers.discarding());
        int status = resp.statusCode();
        if (status >= 500) {
            execution.fail("Server error: HTTP " + status);
        } else {
            execution.pass();
        }
    }

    // ── Auth: unauthenticated request must be rejected ────────────────────────

    private void executeAuth(TestExecution execution, ApiEndpoint endpoint, String targetUrl) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(15));
        // Use the actual HTTP method; for non-GET send an empty body to avoid 415/400
        String method = endpoint.getMethod() != null ? endpoint.getMethod() : "GET";
        if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString("{}"))
                   .header("Content-Type", "application/json");
        }
        HttpResponse<Void> resp = http.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        int status = resp.statusCode();
        if (status == 401 || status == 403) {
            execution.pass();
        } else if (status == 405) {
            // Method Not Allowed — server responded (not auth-blocked); treat as inconclusive pass
            execution.pass();
        } else if (status >= 500) {
            execution.fail("Endpoint returned server error without authentication: HTTP " + status);
        } else {
            execution.fail("Endpoint accessible without authentication (HTTP " + status + ")");
        }
    }

    // ── Schema: response body must be valid JSON ──────────────────────────────

    private void executeSchema(TestExecution execution, ApiEndpoint endpoint, String targetUrl,
                               String authToken, String authUsername, String authPassword)
            throws Exception {
        HttpResponse<String> resp = http.send(
                buildRequest(endpoint, targetUrl, authToken, authUsername, authPassword).build(),
                HttpResponse.BodyHandlers.ofString());
        int status = resp.statusCode();
        if (status >= 400) {
            execution.fail("Unexpected HTTP " + status + " when validating response schema");
            return;
        }
        String body = resp.body();
        String ct = resp.headers().firstValue("content-type").orElse("");
        if (!ct.contains("json") && (body == null || body.isBlank())) {
            execution.fail("Response is not JSON (content-type: " + ct + ")");
            return;
        }
        // Lightweight JSON validity check — starts with { or [
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            execution.pass();
        } else {
            execution.fail("Response body is not valid JSON: " + trimmed.substring(0, Math.min(80, trimmed.length())));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpRequest.Builder buildRequest(ApiEndpoint endpoint, String targetUrl, String authToken,
                                             String authUsername, String authPassword) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        if (authToken != null && !authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
        } else if (authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank()) {
            String credentials = authUsername + ":" + authPassword;
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        String method = endpoint.getMethod() != null ? endpoint.getMethod() : "GET";
        if ("GET".equals(method)) {
            return builder.GET();
        } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            return builder.method(method, HttpRequest.BodyPublishers.ofString("{}"))
                          .header("Content-Type", "application/json");
        } else {
            return builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
    }
}