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
import java.time.Duration;
import java.util.UUID;

@Service
public class ApiExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ApiExecutionService.class);

    private final ApiEndpointRepository endpoints;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ApiExecutionService(ApiEndpointRepository endpoints) {
        this.endpoints = endpoints;
    }

    public void execute(TestExecution execution, GeneratedTest test, String authToken) {
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
        try {
            switch (test.getType()) {
                case "api-smoke"  -> executeSmoke(execution, endpoint, authToken);
                case "api-auth"   -> executeAuth(execution, endpoint);
                case "api-schema" -> executeSchema(execution, endpoint, authToken);
                default           -> execution.fail("Unknown API test type: " + test.getType());
            }
        } catch (Exception e) {
            log.warn("API test execution error for {}: {}", test.getTargetUrl(), e.getMessage());
            execution.fail("Request failed: " + e.getMessage());
        }
    }

    // ── Smoke: endpoint must not return 5xx ───────────────────────────────────

    private void executeSmoke(TestExecution execution, ApiEndpoint endpoint, String authToken)
            throws Exception {
        HttpResponse<Void> resp = http.send(
                buildRequest(endpoint, authToken).build(),
                HttpResponse.BodyHandlers.discarding());
        int status = resp.statusCode();
        if (status >= 500) {
            execution.fail("Server error: HTTP " + status);
        } else {
            execution.pass();
        }
    }

    // ── Auth: unauthenticated request must be rejected ────────────────────────

    private void executeAuth(TestExecution execution, ApiEndpoint endpoint) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.getUrl()))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        int status = resp.statusCode();
        if (status == 401 || status == 403) {
            execution.pass();
        } else if (status >= 500) {
            execution.fail("Endpoint returned server error without authentication: HTTP " + status);
        } else {
            execution.fail("Endpoint accessible without authentication (HTTP " + status + ")");
        }
    }

    // ── Schema: response body must be valid JSON ──────────────────────────────

    private void executeSchema(TestExecution execution, ApiEndpoint endpoint, String authToken)
            throws Exception {
        HttpResponse<String> resp = http.send(
                buildRequest(endpoint, authToken).build(),
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

    private HttpRequest.Builder buildRequest(ApiEndpoint endpoint, String authToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.getUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        if (authToken != null && !authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
        }
        return "GET".equals(endpoint.getMethod()) ? builder.GET()
                : builder.method(endpoint.getMethod(), HttpRequest.BodyPublishers.noBody());
    }
}