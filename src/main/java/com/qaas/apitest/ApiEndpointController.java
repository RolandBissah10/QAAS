package com.qaas.apitest;

import com.qaas.apitest.entity.ApiEndpoint;
import com.qaas.apitest.repository.ApiEndpointRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/api-endpoints")
public class ApiEndpointController {

    private final ApiEndpointRepository repository;

    public ApiEndpointController(ApiEndpointRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/analysis/{analysisId}")
    List<ApiEndpoint> byAnalysis(@PathVariable UUID analysisId) {
        return repository.findByAnalysisId(analysisId);
    }
}