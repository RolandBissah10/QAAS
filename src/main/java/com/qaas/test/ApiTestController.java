package com.qaas.test;

import com.qaas.test.ApiTestDtos.ApiTestRequest;
import com.qaas.test.ApiTestDtos.ApiTestResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tests")
public class ApiTestController {
    private final ApiTestService service;

    public ApiTestController(ApiTestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiTestResponse create(@Valid @RequestBody ApiTestRequest request) {
        return service.create(request);
    }

    @GetMapping
    List<ApiTestResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    ApiTestResponse get(@PathVariable UUID id) {
        return service.find(id);
    }

    @PutMapping("/{id}")
    ApiTestResponse update(@PathVariable UUID id, @Valid @RequestBody ApiTestRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
