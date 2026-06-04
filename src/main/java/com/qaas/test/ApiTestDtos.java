package com.qaas.test;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public final class ApiTestDtos {
    private ApiTestDtos() {
    }

    public record ApiTestRequest(
            @NotNull UUID projectId,
            @NotNull UUID environmentId,
            @NotBlank String name,
            @NotNull RequestMethod method,
            @NotBlank String endpoint,
            Map<String, Object> headers,
            Map<String, Object> requestBody,
            @Min(100) @Max(599) int expectedStatusCode,
            Map<String, Object> expectedResponse
    ) {
    }

    public record ApiTestResponse(
            UUID id,
            UUID projectId,
            UUID environmentId,
            String name,
            RequestMethod method,
            String endpoint,
            Map<String, Object> headers,
            Map<String, Object> requestBody,
            int expectedStatusCode,
            Map<String, Object> expectedResponse
    ) {
        public static ApiTestResponse from(ApiTest test) {
            return new ApiTestResponse(
                    test.getId(),
                    test.getProject().getId(),
                    test.getEnvironment().getId(),
                    test.getName(),
                    test.getMethod(),
                    test.getEndpoint(),
                    test.getHeaders(),
                    test.getRequestBody(),
                    test.getExpectedStatusCode(),
                    test.getExpectedResponse()
            );
        }
    }
}
