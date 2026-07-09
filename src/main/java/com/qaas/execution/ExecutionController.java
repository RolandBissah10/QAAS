package com.qaas.execution;

import com.qaas.common.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {
    private final ExecutionService service;

    public ExecutionController(ExecutionService service) {
        this.service = service;
    }

    @GetMapping("/analysis/{analysisId}")
    PagedResponse<ExecutionDtos.ExecutionResponse> byAnalysis(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.getByAnalysis(analysisId,
                PageRequest.of(page, size, Sort.by("startedAt").descending()));
    }
}
