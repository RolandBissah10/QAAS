package com.qaas.generator;

import com.qaas.common.PagedResponse;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.project.ProjectService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tests")
public class GeneratedTestController {
    private final GeneratedTestRepository tests;
    private final ProjectService projectService;

    public GeneratedTestController(GeneratedTestRepository tests, ProjectService projectService) {
        this.tests = tests;
        this.projectService = projectService;
    }

    @GetMapping("/analysis/{analysisId}")
    PagedResponse<GeneratedTest> byAnalysis(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        projectService.verifyAnalysisAccess(analysisId, auth.getName());
        var paged = tests.findByAnalysisId(analysisId,
                PageRequest.of(page, size, Sort.by("id").ascending()));
        return PagedResponse.from(paged);
    }
}