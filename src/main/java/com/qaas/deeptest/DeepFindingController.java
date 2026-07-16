package com.qaas.deeptest;

import com.qaas.project.ProjectService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deep-findings")
public class DeepFindingController {

    private final DeepFindingRepository repository;
    private final ProjectService projectService;

    public DeepFindingController(DeepFindingRepository repository, ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    @GetMapping("/analysis/{analysisId}")
    public List<DeepFinding> byAnalysis(@PathVariable UUID analysisId, Authentication auth) {
        projectService.verifyAnalysisAccess(analysisId, auth.getName());
        return repository.findByAnalysisIdOrderByDetectedAtDesc(analysisId);
    }
}