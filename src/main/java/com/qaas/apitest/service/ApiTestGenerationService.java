package com.qaas.apitest.service;

import com.qaas.apitest.entity.ApiEndpoint;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApiTestGenerationService {

    private final GeneratedTestRepository tests;

    public ApiTestGenerationService(GeneratedTestRepository tests) {
        this.tests = tests;
    }

    public List<GeneratedTest> generateForEndpoint(ApiEndpoint endpoint) {
        List<GeneratedTest> result = new ArrayList<>();
        String path = extractPath(endpoint.getUrl());
        String label = endpoint.getMethod() + " " + path;

        // Smoke: endpoint must not return a 5xx error
        result.add(save(endpoint, "API Smoke: " + label + " responds without server error", "api-smoke"));

        // Auth: endpoint behind authentication must reject unauthenticated calls
        if (endpoint.isRequiresAuth()) {
            result.add(save(endpoint, "API Auth: " + label + " requires authentication", "api-auth"));
        }

        // Schema: for GET endpoints that returned data, validate response is valid JSON
        if ("GET".equals(endpoint.getMethod()) && endpoint.getObservedStatus() != null
                && endpoint.getObservedStatus() < 400) {
            result.add(save(endpoint, "API Schema: " + label + " returns valid JSON", "api-schema"));
        }

        return result;
    }

    private GeneratedTest save(ApiEndpoint endpoint, String name, String type) {
        GeneratedTest t = new GeneratedTest();
        t.setAnalysisId(endpoint.getAnalysisId());
        t.setApiEndpointId(endpoint.getId());
        t.setTargetUrl(endpoint.getUrl());
        t.setName(name);
        t.setType(type);
        t.setStatus("generated");
        return tests.save(t);
    }

    private static String extractPath(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            return (path == null || path.isBlank()) ? "/" : path;
        } catch (Exception e) {
            return url;
        }
    }
}