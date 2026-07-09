package com.qaas.generator;

import com.qaas.common.PagedResponse;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tests")
public class GeneratedTestController {
    private final GeneratedTestRepository tests;

    public GeneratedTestController(GeneratedTestRepository tests) {
        this.tests = tests;
    }

    @GetMapping("/analysis/{analysisId}")
    PagedResponse<GeneratedTest> byAnalysis(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var paged = tests.findByAnalysisId(analysisId,
                PageRequest.of(page, size, Sort.by("id").ascending()));
        return PagedResponse.from(paged);
    }
}
