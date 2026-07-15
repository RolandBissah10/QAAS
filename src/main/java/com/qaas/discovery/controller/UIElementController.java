package com.qaas.discovery.controller;

import com.qaas.discovery.entity.UIElement;
import com.qaas.discovery.repository.UIElementRepository;
import com.qaas.exception.NotFoundException;
import com.qaas.page.repository.PageRepository;
import com.qaas.project.ProjectService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ui-elements")
public class UIElementController {

    private final UIElementRepository repository;
    private final PageRepository pages;
    private final ProjectService projectService;

    public UIElementController(UIElementRepository repository, PageRepository pages, ProjectService projectService) {
        this.repository = repository;
        this.pages = pages;
        this.projectService = projectService;
    }

    @GetMapping("/page/{pageId}")
    List<UIElement> byPage(@PathVariable UUID pageId, Authentication auth) {
        var page = pages.findById(pageId)
                .orElseThrow(() -> new NotFoundException("Page not found"));
        if (page.getAnalysisId() == null) throw new NotFoundException("Page not found");
        projectService.verifyAnalysisAccess(page.getAnalysisId(), auth.getName());
        return repository.findByPageId(pageId);
    }
}