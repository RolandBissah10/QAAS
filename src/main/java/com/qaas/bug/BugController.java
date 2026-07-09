package com.qaas.bug;

import com.qaas.common.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/bugs")
public class BugController {
    private final BugDetectionService service;

    public BugController(BugDetectionService service) {
        this.service = service;
    }

    @GetMapping("/analysis/{analysisId}")
    PagedResponse<BugDtos.BugResponse> byAnalysis(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.getByAnalysis(analysisId, PageRequest.of(page, size, Sort.by("detectedAt").descending()));
    }

    record StatusRequest(BugStatus status) {}

    @PatchMapping("/{id}/status")
    ResponseEntity<BugDtos.BugResponse> updateStatus(@PathVariable UUID id, @RequestBody StatusRequest body) {
        return ResponseEntity.ok(service.updateStatus(id, body.status()));
    }
}