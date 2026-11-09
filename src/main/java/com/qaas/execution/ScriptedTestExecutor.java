package com.qaas.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaas.common.ApplicationUrlScope;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Interprets an AI-generated JSON test script step-by-step using Playwright.
 * Returns null on full pass, or the first failing step's error message.
 */
@Component
public class ScriptedTestExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptedTestExecutor.class);

    private final ObjectMapper objectMapper;

    public ScriptedTestExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String execute(String scriptJson, String targetUrl, BrowserContext context) {
        if (scriptJson == null || scriptJson.isBlank()) return "No test script provided";
        JsonNode script;
        try {
            script = objectMapper.readTree(scriptJson);
        } catch (Exception e) {
            return "Script execution error: " + e.getMessage();
        }
        JsonNode steps = script.isArray() ? script : script.path("steps");
        if (!steps.isArray()) return "Test script must contain a steps array";
        Set<String> allowedUrls = new HashSet<>();
        if (!script.isArray()) {
            JsonNode allowedUrlNodes = script.path("allowedUrls");
            if (!allowedUrlNodes.isArray()) return "Journey script must contain discovered allowedUrls";
            allowedUrlNodes.forEach(url -> {
                if (url.isTextual()) allowedUrls.add(url.asText());
            });
            if (!isDiscoveredPage(targetUrl, targetUrl, allowedUrls)) {
                return "Journey start URL is not in the discovered page list";
            }
        }

        Page page = context.newPage();
        try {
            page.setDefaultNavigationTimeout(15000);
            page.setDefaultTimeout(8000);

            // Navigate to the target URL before running the script
            page.navigate(targetUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
            if (!ApplicationUrlScope.isSameOrigin(targetUrl, page.url())) {
                return "Initial navigation left the target application origin";
            }

            for (JsonNode step : steps) {
                String action  = step.path("action").asText("");
                String selector = step.path("selector").asText(null);
                String value   = step.path("value").asText(null);
                String contains = step.path("contains").asText(null);
                int timeout    = step.path("timeout").asInt(1500);

                if ("navigate".equals(action)) {
                    String navigationUrl = value != null ? value : selector;
                    if (navigationUrl != null && !navigationUrl.isBlank()
                            && !ApplicationUrlScope.isSameOrigin(targetUrl, navigationUrl)) {
                        return "navigate step targets a URL outside the target application origin";
                    }
                }

                String error = executeStep(page, action, selector, value, contains, timeout);
                if (error != null) return error;
                if (!ApplicationUrlScope.isSameOrigin(targetUrl, page.url())) {
                    return "Script navigation left the target application origin";
                }
                if (!allowedUrls.isEmpty() && !isDiscoveredPage(targetUrl, page.url(), allowedUrls)) {
                    return "Journey navigated to a page that was not discovered for this application";
                }
            }
            return null;
        } catch (Exception e) {
            return "Script execution error: " + e.getMessage();
        } finally {
            try { page.close(); } catch (Exception ignored) {}
        }
    }

    private boolean isDiscoveredPage(String originUrl, String candidateUrl, Set<String> allowedUrls) {
        try {
            URI candidate = URI.create(candidateUrl);
            String candidatePath = normalizePath(candidate.getPath());
            return allowedUrls.stream().anyMatch(allowedUrl -> {
                if (!ApplicationUrlScope.isSameOrigin(originUrl, allowedUrl)
                        || !ApplicationUrlScope.isSameOrigin(originUrl, candidateUrl)) {
                    return false;
                }
                return candidatePath.equals(normalizePath(URI.create(allowedUrl).getPath()));
            });
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) return "/";
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private String executeStep(Page page, String action, String selector,
                               String value, String contains, int timeout) {
        try {
            switch (action) {
                case "navigate" -> {
                    String url = value != null ? value : selector;
                    if (url == null || url.isBlank()) return "navigate step missing url/value";
                    page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
                }
                case "fill" -> {
                    if (selector == null) return "fill step missing selector";
                    Locator loc = page.locator(selector).first();
                    loc.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                    loc.fill(value != null ? value : "");
                }
                case "click" -> {
                    if (selector == null) return "click step missing selector";
                    Locator loc = page.locator(selector).first();
                    loc.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                    loc.click();
                }
                case "select" -> {
                    if (selector == null) return "select step missing selector";
                    page.locator(selector).first().selectOption(value != null ? value : "");
                }
                case "assertText" -> {
                    if (selector == null) return "assertText step missing selector";
                    String text = page.locator(selector).first().innerText();
                    if (contains != null && !text.contains(contains))
                        return "assertText failed: expected \"" + contains + "\" but got \"" + snippet(text) + "\"";
                }
                case "assertUrl" -> {
                    String url = page.url();
                    if (contains != null && !url.contains(contains))
                        return "assertUrl failed: expected URL to contain \"" + contains + "\" but got \"" + url + "\"";
                }
                case "assertVisible" -> {
                    if (selector == null) return "assertVisible step missing selector";
                    if (!page.locator(selector).first().isVisible())
                        return "assertVisible failed: \"" + selector + "\" is not visible";
                }
                case "assertNotVisible" -> {
                    if (selector == null) return "assertNotVisible step missing selector";
                    if (page.locator(selector).first().isVisible())
                        return "assertNotVisible failed: \"" + selector + "\" should not be visible";
                }
                case "waitForSelector" -> {
                    if (selector == null) return "waitForSelector step missing selector";
                    page.locator(selector).first()
                        .waitFor(new Locator.WaitForOptions().setTimeout(timeout));
                }
                case "waitForTimeout" -> page.waitForTimeout(timeout);
                default -> log.debug("Unknown scripted step action: {}", action);
            }
            return null;
        } catch (Exception e) {
            return "Step \"" + action + "\""
                    + (selector != null ? " on \"" + selector + "\"" : "")
                    + " failed: " + e.getMessage();
        }
    }

    private String snippet(String s) {
        return s == null ? "" : s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}