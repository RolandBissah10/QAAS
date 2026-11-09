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
import java.util.Map;
import java.util.Set;

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
                log.warn("AI provider returned no JSON array for {}", page.getUrl());
                return result;
            }

            JsonNode generated = objectMapper.readTree(scriptJson);
            if (!generated.isArray() || generated.isEmpty()) return result;

            if (generated.get(0).isObject() && generated.get(0).has("steps")) {
                int scenarioNumber = 0;
                for (JsonNode scenario : generated) {
                    JsonNode steps = scenario.path("steps");
                    if (!steps.isArray() || steps.isEmpty()) continue;

                    String name = scenario.path("name").asText("").trim();
                    if (name.isEmpty()) name = "Scenario " + (++scenarioNumber);
                    else scenarioNumber++;
                    result.add(saveTest(page, name, objectMapper.writeValueAsString(steps)));
                }
            } else {
                result.add(saveTest(page, "Functional test", scriptJson));
            }
            log.info("Generated {} AI scripted test(s) for {}", result.size(), page.getUrl());

        } catch (Exception e) {
            log.warn("AI test generation failed for {}: {}", page.getUrl(), e.getMessage());
        }
        return result;
    }

    public List<GeneratedTest> generateJourneys(List<Page> pages, String baseUrl, boolean authenticated) {
        if (!claude.isConfigured() || pages == null || pages.size() < 2) return List.of();

        try {
            List<Page> contextPages = pages.stream().limit(12).toList();
            Set<String> knownUrls = contextPages.stream()
                    .map(Page::getUrl)
                    .collect(java.util.stream.Collectors.toSet());
            StringBuilder pageContext = new StringBuilder();
            for (Page page : contextPages) {
                pageContext.append("\nPage: ").append(page.getUrl())
                        .append(" | title: ").append(page.getTitle() == null ? "" : page.getTitle())
                        .append(" | type: ").append(page.getPageType()).append('\n');
                uiElements.findByPageId(page.getId()).stream().limit(25).forEach(element ->
                        pageContext.append("  ").append(element.getElementType())
                                .append(" | selector: ").append(element.getSelector())
                                .append(" | label: ").append(element.getLabel()).append('\n'));
            }

            String prompt = """
                    You are a QA engineer creating end-to-end browser journeys for one web application.
                    Application origin: %s
                    Authenticated crawl state is available: %s

                    Only these pages and their observed controls were discovered:
                    %s

                    Generate 1–3 realistic multi-page user journeys. Each journey must start at one
                    listed page and use only controls shown for the current page. Prefer clicking
                    observed links/buttons to move between pages. Never invent routes, selectors,
                    controls, credentials, or data that are not supported by this inventory.
                    When authenticated state is available, prioritize useful signed-in workflows.
                    When it is not, use only public workflows. Use safe, clearly synthetic form values.
                    End each journey with an assertion of an observable result. Do not include
                    destructive or irreversible actions.

                    Return ONLY JSON in this format:
                    [{"name":"Descriptive journey","startUrl":"exact listed page URL",
                      "steps":[{"action":"click","selector":"..."},
                               {"action":"assertVisible","selector":"..."}]}]

                    Supported actions: navigate, fill, click, select, assertUrl, assertText,
                    assertVisible, assertNotVisible, waitForSelector, waitForTimeout.
                    For navigate steps, use only an exact URL from the discovered page list.
                    """.formatted(baseUrl, authenticated, pageContext);

            String response = claude.complete(prompt);
            String json = extractJsonArray(response);
            if (json == null) {
                log.warn("AI provider returned no journey JSON for {}", baseUrl);
                return List.of();
            }

            JsonNode scenarios = objectMapper.readTree(json);
            if (!scenarios.isArray()) return List.of();
            List<GeneratedTest> result = new ArrayList<>();
            for (JsonNode scenario : scenarios) {
                if (result.size() >= 3) break;
                String startUrl = scenario.path("startUrl").asText("");
                JsonNode steps = scenario.path("steps");
                if (!knownUrls.contains(startUrl) || !steps.isArray() || steps.isEmpty()
                        || !isJourneyWithinDiscoveredPages(steps, knownUrls)) {
                    log.warn("Ignoring AI journey with an unknown page or invalid steps for {}", baseUrl);
                    continue;
                }

                Page startPage = contextPages.stream()
                        .filter(page -> page.getUrl().equals(startUrl))
                        .findFirst().orElseThrow();
                GeneratedTest test = new GeneratedTest();
                test.setPageId(startPage.getId());
                test.setTargetUrl(startUrl);
                String name = scenario.path("name").asText("").trim();
                test.setName("AI Journey: " + (name.isEmpty() ? "Application workflow" : name));
                test.setType("scripted");
                test.setStatus("generated");
                test.setScriptJson(objectMapper.writeValueAsString(Map.of(
                        "allowedUrls", contextPages.stream().map(Page::getUrl).toList(),
                        "steps", steps)));
                result.add(repository.save(test));
            }
            log.info("Generated {} end-to-end journey(s) for {}", result.size(), baseUrl);
            return result;
        } catch (Exception e) {
            log.warn("AI journey generation failed for {}: {}", baseUrl, e.getMessage());
            return List.of();
        }
    }

    private boolean isJourneyWithinDiscoveredPages(JsonNode steps, Set<String> knownUrls) {
        for (JsonNode step : steps) {
            String action = step.path("action").asText("");
            if (!Set.of("navigate", "fill", "click", "select", "assertUrl", "assertText",
                    "assertVisible", "assertNotVisible", "waitForSelector", "waitForTimeout").contains(action)) {
                return false;
            }
            if ("navigate".equals(action)) {
                String url = step.path("value").asText(step.path("selector").asText(""));
                if (!knownUrls.contains(url)) return false;
            }
        }
        return true;
    }

    private String buildPrompt(Page page, List<UIElement> elements, String stripped) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a QA automation engineer. Generate a functional Playwright test scoped to this application.\n\n");
        sb.append("Application and page URL: ").append(page.getUrl()).append("\n");
        sb.append("Page type: ").append(page.getPageType()).append("\n\n");

        if (!elements.isEmpty()) {
            sb.append("Interactive elements:\n");
            elements.stream().limit(80).forEach(e ->
                sb.append("  ").append(e.getElementType())
                  .append(" | selector: ").append(e.getSelector())
                  .append(" | label: ").append(e.getLabel()).append("\n")
            );
            sb.append("\n");
        }

        sb.append("Relevant HTML:\n").append(stripped).append("\n\n");

        sb.append("""
            Generate 2–4 independent functional test scenarios for this page when its controls \
            support them. Each scenario must test a distinct user goal, not merely repeat a page-load \
            check. Include enough steps to exercise the relevant interaction and verify its outcome.

            Rules:
            - ONLY use selectors and elements that are explicitly listed above in "Interactive elements" or visible in the "Relevant HTML". Do NOT invent elements, buttons, or actions that are not present in the provided content.
            - Keep every action on this page. Do NOT navigate to, suggest, or test unrelated pages, URLs, domains, or subdomains.
            - Do NOT invent application routes or assume that a submission succeeds without an observable assertion.
            - Cover different visible controls, validation states, filtering, or navigation states where those features are present in the supplied page context.
            - If the page has no interactive elements, return one scenario with a simple assertText or assertVisible step based on content that IS present.
            - Use realistic but safe test data (e.g. test@example.com, TestPassword1!)
            - End each scenario with an assertion that verifies an observable result.
            - Keep selectors simple and robust (prefer type/name/placeholder over deeply nested paths)

            Respond ONLY with a JSON array of scenarios — no markdown, no explanation. Each item must have
            a descriptive "name" and a "steps" array:
            [{"name":"Scenario name","steps":[{"action":"..."}]}]

            Step schema:
            { "action": "fill|click|select|assertUrl|assertText|assertVisible|assertNotVisible|waitForSelector|waitForTimeout",
              "selector": "<css selector>",   // required for fill, click, assert*, waitForSelector
              "value": "<string>",            // required for fill
              "contains": "<substring>",      // for assertUrl, assertText
              "timeout": <ms number>          // for waitForTimeout, waitForSelector
            }

            Each step must use the existing action schema below. Supported actions:
            fill, click, select, assertUrl, assertText, assertVisible, assertNotVisible,
            waitForSelector, waitForTimeout.
            """);

        return sb.toString();
    }

    private GeneratedTest saveTest(Page page, String scenarioName, String scriptJson) {
        GeneratedTest test = new GeneratedTest();
        test.setPageId(page.getId());
        test.setTargetUrl(page.getUrl());
        test.setName("AI: " + scenarioName + " — " + page.getUrl());
        test.setType("scripted");
        test.setStatus("generated");
        test.setScriptJson(scriptJson);
        return repository.save(test);
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