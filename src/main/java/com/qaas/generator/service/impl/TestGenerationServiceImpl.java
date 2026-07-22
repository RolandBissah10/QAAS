package com.qaas.generator.service.impl;

import com.qaas.discovery.entity.UIElement;
import com.qaas.discovery.repository.UIElementRepository;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.generator.service.AiTestGenerationService;
import com.qaas.generator.service.TestGenerationService;
import com.qaas.page.entity.Page;
import com.qaas.page.repository.PageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TestGenerationServiceImpl implements TestGenerationService {

    private final GeneratedTestRepository repository;
    private final UIElementRepository uiElements;
    private final PageRepository pages;
    private final AiTestGenerationService aiTestGenerationService;

    public TestGenerationServiceImpl(GeneratedTestRepository repository,
                                     UIElementRepository uiElements,
                                     PageRepository pages,
                                     AiTestGenerationService aiTestGenerationService) {
        this.repository = repository;
        this.uiElements = uiElements;
        this.pages = pages;
        this.aiTestGenerationService = aiTestGenerationService;
    }

    @Override
    public List<GeneratedTest> generateForPage(UUID pageId, String pageUrl, String htmlContent) {
        Page page = pages.findById(pageId).orElse(null);
        String pageType = (page != null && page.getPageType() != null) ? page.getPageType() : "GENERAL";
        List<UIElement> elements = uiElements.findByPageId(pageId);

        boolean hasForm = elements.stream().anyMatch(e -> "form".equals(e.getElementType()));
        boolean hasInputs = elements.stream().anyMatch(e -> "input".equals(e.getElementType()));

        List<GeneratedTest> out = new ArrayList<>();

        // Every page gets a smoke test
        out.add(save(pageId, pageUrl, "Smoke: page loads without errors", "smoke"));

        switch (pageType) {
            case "LOGIN" -> {
                out.add(save(pageId, pageUrl, "Auth: submit login form with test credentials", "auth"));
                if (hasForm || hasInputs)
                    out.add(save(pageId, pageUrl, "Validation: empty login form triggers validation", "functional"));
            }
            case "REGISTER" -> {
                out.add(save(pageId, pageUrl, "Auth: submit registration form with test data", "auth"));
                if (hasForm || hasInputs)
                    out.add(save(pageId, pageUrl, "Validation: empty registration form triggers validation", "functional"));
            }
            case "CHECKOUT" -> {
                out.add(save(pageId, pageUrl, "Functional: empty checkout submission is handled safely", "functional"));
                out.add(save(pageId, pageUrl, "Smoke: checkout page UI elements present", "smoke"));
            }
            case "PRODUCT" -> {
                // No hardcoded template — the AI-generated test handles product pages
                // based on what elements are actually present in the HTML
            }
            default -> {
                if (hasForm || hasInputs)
                    out.add(save(pageId, pageUrl, "Functional: form on page submits without server error", "functional"));
                out.add(save(pageId, pageUrl, "Smoke: no broken links or console errors", "smoke"));
            }
        }

        // AI-generated functional test (only when Claude is configured and page object is available)
        if (page != null && htmlContent != null && !htmlContent.isBlank()) {
            out.addAll(aiTestGenerationService.generateForPage(page, htmlContent));
        }

        return out;
    }

    private GeneratedTest save(UUID pageId, String pageUrl, String name, String type) {
        GeneratedTest t = new GeneratedTest();
        t.setPageId(pageId);
        t.setTargetUrl(pageUrl);
        t.setName(name);
        t.setType(type);
        t.setStatus("generated");
        return repository.save(t);
    }
}