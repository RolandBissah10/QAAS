package com.qaas.apitest;

import com.qaas.apitest.entity.ApiEndpoint;
import com.qaas.apitest.repository.ApiEndpointRepository;
import com.qaas.project.ProjectService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/api-endpoints")
public class ApiEndpointController {

    private final ApiEndpointRepository repository;
    private final ProjectService projectService;

    public ApiEndpointController(ApiEndpointRepository repository, ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    @GetMapping("/analysis/{analysisId}")
    List<ApiEndpoint> byAnalysis(@PathVariable UUID analysisId, Authentication auth) {
        projectService.verifyAnalysisAccess(analysisId, auth.getName());
        return repository.findByAnalysisId(analysisId);
    }
}