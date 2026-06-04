package com.qaas.test;

import com.qaas.common.JsonMaps;
import com.qaas.environment.EnvironmentService;
import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectService;
import com.qaas.test.ApiTestDtos.ApiTestRequest;
import com.qaas.test.ApiTestDtos.ApiTestResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ApiTestService {
    private final ApiTestRepository tests;
    private final ProjectService projects;
    private final EnvironmentService environments;

    public ApiTestService(ApiTestRepository tests, ProjectService projects, EnvironmentService environments) {
        this.tests = tests;
        this.projects = projects;
        this.environments = environments;
    }

    @Transactional
    public ApiTestResponse create(ApiTestRequest request) {
        return ApiTestResponse.from(tests.save(toEntity(new ApiTest(), request)));
    }

    @Transactional(readOnly = true)
    public List<ApiTestResponse> list() {
        return tests.findAll().stream().map(ApiTestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ApiTest get(UUID id) {
        return tests.findById(id).orElseThrow(() -> new NotFoundException("API test not found"));
    }

    @Transactional(readOnly = true)
    public ApiTestResponse find(UUID id) {
        return ApiTestResponse.from(get(id));
    }

    @Transactional
    public ApiTestResponse update(UUID id, ApiTestRequest request) {
        return ApiTestResponse.from(toEntity(get(id), request));
    }

    @Transactional
    public void delete(UUID id) {
        tests.delete(get(id));
    }

    private ApiTest toEntity(ApiTest test, ApiTestRequest request) {
        test.update(
                projects.get(request.projectId()),
                environments.get(request.environmentId()),
                request.name(),
                request.method(),
                request.endpoint(),
                JsonMaps.copyOrEmpty(request.headers()),
                JsonMaps.copyOrEmpty(request.requestBody()),
                request.expectedStatusCode(),
                JsonMaps.copyOrEmpty(request.expectedResponse())
        );
        return test;
    }
}
