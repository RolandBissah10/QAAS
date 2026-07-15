package com.qaas.execution;

import com.qaas.common.PagedResponse;
import com.qaas.project.ProjectService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {
    private final ExecutionService service;
    private final ProjectService projectService;

    public ExecutionController(ExecutionService service, ProjectService projectService) {
        this.service = service;
        this.projectService = projectService;
    }

    @GetMapping("/analysis/{analysisId}")
    PagedResponse<ExecutionDtos.ExecutionResponse> byAnalysis(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        projectService.verifyAnalysisAccess(analysisId, auth.getName());
        return service.getByAnalysis(analysisId,
                PageRequest.of(page, size, Sort.by("startedAt").descending()));
    }
}