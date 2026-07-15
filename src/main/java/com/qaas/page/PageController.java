package com.qaas.page;

import com.qaas.common.PagedResponse;
import com.qaas.page.entity.Page;
import com.qaas.page.repository.PageRepository;
import com.qaas.project.ProjectService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/pages")
public class PageController {
    private final PageRepository pages;
    private final ProjectService projectService;

    public PageController(PageRepository pages, ProjectService projectService) {
        this.pages = pages;
        this.projectService = projectService;
    }

    @GetMapping("/analysis/{analysisId}")
    PagedResponse<Page> byAnalysis(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        projectService.verifyAnalysisAccess(analysisId, auth.getName());
        var paged = pages.findByAnalysisId(analysisId,
                PageRequest.of(page, size, Sort.by("id").ascending()));
        return PagedResponse.from(paged);
    }
}