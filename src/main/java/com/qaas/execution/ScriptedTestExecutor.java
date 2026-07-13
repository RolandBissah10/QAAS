package com.qaas.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
        Page page = context.newPage();
        try {
            page.setDefaultNavigationTimeout(15000);
            page.setDefaultTimeout(8000);

            // Navigate to the target URL before running the script
            page.navigate(targetUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));

            JsonNode steps = objectMapper.readTree(scriptJson);
            for (JsonNode step : steps) {
                String action  = step.path("action").asText("");
                String selector = step.path("selector").asText(null);
                String value   = step.path("value").asText(null);
                String contains = step.path("contains").asText(null);
                int timeout    = step.path("timeout").asInt(1500);

                String error = executeStep(page, action, selector, value, contains, timeout);
                if (error != null) return error;
            }
            return null;
        } catch (Exception e) {
            return "Script execution error: " + e.getMessage();
        } finally {
            try { page.close(); } catch (Exception ignored) {}
        }
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