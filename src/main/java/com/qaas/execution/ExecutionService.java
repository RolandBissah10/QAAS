package com.qaas.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaas.collection.TestCollectionService;
import com.qaas.result.ResultDtos.ResultResponse;
import com.qaas.result.TestResult;
import com.qaas.result.TestResultRepository;
import com.qaas.test.ApiTest;
import com.qaas.test.ApiTestService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutionService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ApiTestService tests;
    private final TestCollectionService collections;
    private final TestExecutionRepository executions;
    private final TestResultRepository results;
    private final ObjectMapper objectMapper;

    public ExecutionService(ApiTestService tests, TestCollectionService collections, TestExecutionRepository executions,
                            TestResultRepository results, ObjectMapper objectMapper) {
        this.tests = tests;
        this.collections = collections;
        this.executions = executions;
        this.results = results;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResultResponse runTest(UUID id) {
        return execute(tests.get(id));
    }

    @Transactional
    public ExecutionDtos.CollectionExecutionResponse runCollection(UUID id) {
        List<ResultResponse> resultList = collections.get(id).getTests().stream().map(this::execute).toList();
        long passed = resultList.stream().filter(ResultResponse::passed).count();
        return new ExecutionDtos.CollectionExecutionResponse(resultList, passed, resultList.size() - passed);
    }

    private ResultResponse execute(ApiTest test) {
        long started = System.nanoTime();
        Map<String, Object> responseBody = new LinkedHashMap<>();
        boolean passed = false;
        ExecutionStatus status = ExecutionStatus.ERROR;
        try {
            Response response = send(test);
            responseBody = parseBody(response.asString());
            passed = response.statusCode() == test.getExpectedStatusCode()
                    && (test.getExpectedResponse().isEmpty() || test.getExpectedResponse().equals(responseBody));
            status = passed ? ExecutionStatus.PASSED : ExecutionStatus.FAILED;
        } catch (RuntimeException ex) {
            responseBody = Map.of("error", ex.getMessage());
        }
        long elapsedMs = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        TestExecution execution = executions.save(new TestExecution(test, status));
        return ResultResponse.from(results.save(new TestResult(execution, elapsedMs, responseBody, passed)));
    }

    private Response send(ApiTest test) {
        RequestSpecification request = RestAssured.given().relaxedHTTPSValidation().contentType(ContentType.JSON);
        test.getHeaders().forEach((key, value) -> request.header(key, value == null ? "" : value.toString()));
        if (!test.getRequestBody().isEmpty()) {
            request.body(test.getRequestBody());
        }
        return request.when().request(test.getMethod().name(), url(test));
    }

    private String url(ApiTest test) {
        return UriComponentsBuilder.fromHttpUrl(test.getEnvironment().getBaseUrl())
                .path(test.getEndpoint())
                .toUriString();
    }

    private Map<String, Object> parseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(rawBody, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("raw", rawBody);
        }
    }
}
