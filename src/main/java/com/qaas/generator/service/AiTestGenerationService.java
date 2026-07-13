package com.qaas.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaas.ai.ClaudeClient;
import com.qaas.ai.HtmlSummarizer;
import com.qaas.discovery.entity.UIElement;
import com.qaas.discovery.repository.UIElementRepository;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.repository.GeneratedTestRepository;
import com.qaas.page.entity.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiTestGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiTestGenerationService.class);

    private final ClaudeClient claude;
    private final HtmlSummarizer summarizer;
    private final GeneratedTestRepository repository;
    private final UIElementRepository uiElements;
    private final ObjectMapper objectMapper;

    public AiTestGenerationService(ClaudeClient claude,
                                   HtmlSummarizer summarizer,
                                   GeneratedTestRepository repository,
                                   UIElementRepository uiElements,
                                   ObjectMapper objectMapper) {
        this.claude = claude;
        this.summarizer = summarizer;
        this.repository = repository;
        this.uiElements = uiElements;
        this.objectMapper = objectMapper;
    }

    public List<GeneratedTest> generateForPage(Page page, String htmlContent) {
        List<GeneratedTest> result = new ArrayList<>();
        if (!claude.isConfigured()) return result;

        try {
            List<UIElement> elements = uiElements.findByPageId(page.getId());
            String stripped = summarizer.summarize(htmlContent);
            if (stripped.isBlank()) return result;

            String prompt = buildPrompt(page, elements, stripped);
            String response = claude.complete(prompt);
            String scriptJson = extractJsonArray(response);

            if (scriptJson == null) {
                log.warn("Claude returned no JSON array for {}", page.getUrl());
                return result;
            }

            JsonNode node = objectMapper.readTree(scriptJson);
            if (!node.isArray() || node.isEmpty()) return result;

            GeneratedTest test = new GeneratedTest();
            test.setPageId(page.getId());
            test.setTargetUrl(page.getUrl());
            test.setName("AI: Functional test — " + page.getUrl());
            test.setType("scripted");
            test.setStatus("generated");
            test.setScriptJson(scriptJson);
            result.add(repository.save(test));
            log.info("Generated AI scripted test for {}", page.getUrl());

        } catch (Exception e) {
            log.warn("AI test generation failed for {}: {}", page.getUrl(), e.getMessage());
        }
        return result;
    }

    private String buildPrompt(Page page, List<UIElement> elements, String stripped) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a QA automation engineer. Generate a functional Playwright test for this web page.\n\n");
        sb.append("URL: ").append(page.getUrl()).append("\n");
        sb.append("Page type: ").append(page.getPageType()).append("\n\n");

        if (!elements.isEmpty()) {
            sb.append("Interactive elements:\n");
            elements.stream().limit(20).forEach(e ->
                sb.append("  ").append(e.getElementType())
                  .append(" | selector: ").append(e.getSelector())
                  .append(" | label: ").append(e.getLabel()).append("\n")
            );
            sb.append("\n");
        }

        sb.append("Relevant HTML:\n").append(stripped).append("\n\n");

        sb.append("""
            Generate exactly ONE functional test that does a real interaction (fill a form, \
            click a button, verify a result) — not just a page load check.

            Rules:
            - Use realistic but safe test data (e.g. test@example.com, TestPassword1!)
            - Assert on something that proves the action worked (URL change, text appearing, etc.)
            - Keep selectors simple and robust (prefer type/name/placeholder over deeply nested paths)
            - For login pages: attempt login and assert the URL changes or an element appears
            - For forms: fill fields and submit, then assert the response
            - For product pages: look for an add-to-cart or similar button and click it

            Respond ONLY with a JSON array — no markdown, no explanation, just the array.

            Step schema:
            { "action": "fill|click|assertUrl|assertText|assertVisible|assertNotVisible|waitForSelector|waitForTimeout",
              "selector": "<css selector>",   // required for fill, click, assert*, waitForSelector
              "value": "<string>",            // required for fill
              "contains": "<substring>",      // for assertUrl, assertText
              "timeout": <ms number>          // for waitForTimeout, waitForSelector
            }

            Example (login page):
            [
              {"action":"fill","selector":"input[type='email']","value":"test@example.com"},
              {"action":"fill","selector":"input[type='password']","value":"TestPassword1!"},
              {"action":"click","selector":"button[type='submit']"},
              {"action":"waitForTimeout","timeout":2000},
              {"action":"assertUrl","contains":"/dashboard"}
            ]
            """);

        return sb.toString();
    }

    private String extractJsonArray(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            int closing = text.lastIndexOf("```");
            if (nl >= 0 && closing > nl) text = text.substring(nl + 1, closing).trim();
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : null;
    }
}