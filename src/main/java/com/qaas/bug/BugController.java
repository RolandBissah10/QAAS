package com.qaas.bug;

import com.qaas.common.PagedResponse;
import com.qaas.project.ProjectService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/bugs")
public class BugController {
    private final BugDetectionService service;
    private final ProjectService projectService;

    public BugController(BugDetectionService service, ProjectService projectService) {
        this.service = service;
        this.projectService = projectService;
    }

    @GetMapping("/analysis/{analysisId}")
    PagedResponse<BugDtos.BugResponse> byAnalysis(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        projectService.verifyAnalysisAccess(analysisId, auth.getName());
        return service.getByAnalysis(analysisId, PageRequest.of(page, size, Sort.by("detectedAt").descending()));
    }

    record StatusRequest(BugStatus status) {}

    @PatchMapping("/{id}/status")
    ResponseEntity<BugDtos.BugResponse> updateStatus(@PathVariable UUID id, @RequestBody StatusRequest body) {
        return ResponseEntity.ok(service.updateStatus(id, body.status()));
    }
}