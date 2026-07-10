package com.qaas.execution;

import com.microsoft.playwright.*;
import com.qaas.apitest.service.ApiExecutionService;
import com.qaas.common.PagedResponse;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.playwright.service.PlaywrightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final TestExecutionRepository executions;
    private final PlaywrightService playwright;
    private final ApiExecutionService apiExecutionService;

    public ExecutionService(TestExecutionRepository executions, PlaywrightService playwright,
                            ApiExecutionService apiExecutionService) {
        this.executions = executions;
        this.playwright = playwright;
        this.apiExecutionService = apiExecutionService;
    }

    /** Called from the analysis pipeline — reuses a shared authenticated browser context. */
    @Transactional
    public TestExecution execute(GeneratedTest test, UUID analysisId, BrowserContext sharedContext) {
        TestExecution execution = executions.save(new TestExecution(test));
        try {
            String failReason = runTest(test, sharedContext);
            if (failReason == null) execution.pass();
            else execution.fail(failReason);
            try { playwright.captureScreenshot(analysisId, test.getTargetUrl()); } catch (Exception ignored) {}
        } catch (Exception ex) {
            log.warn("Execution error for '{}': {}", test.getName(), ex.getMessage());
            execution.fail("Browser error: " + ex.getMessage());
        }
        return executions.save(execution);
    }

    /** Standalone variant — opens its own browser (used by PlaywrightTestRunnerService). */
    @Transactional
    public TestExecution execute(GeneratedTest test, UUID analysisId) {
        TestExecution execution = executions.save(new TestExecution(test));
        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext context = browser.newContext();
            try {
                String failReason = runTest(test, context);
                if (failReason == null) execution.pass();
                else execution.fail(failReason);
                try { playwright.captureScreenshot(analysisId, test.getTargetUrl()); } catch (Exception ignored) {}
            } finally {
                context.close();
                browser.close();
            }
        } catch (Exception ex) {
            log.warn("Execution error for '{}': {}", test.getName(), ex.getMessage());
            if (execution.getErrorMessage() == null) execution.fail("Browser error: " + ex.getMessage());
        }
        return executions.save(execution);
    }

    /** Executes an API test (api-smoke / api-auth / api-schema) via HttpClient — no browser needed. */
    @Transactional
    public TestExecution executeApiTest(GeneratedTest test, UUID analysisId, String authToken) {
        TestExecution execution = executions.save(new TestExecution(test));
        try {
            apiExecutionService.execute(execution, test, authToken);
        } catch (Exception ex) {
            log.warn("API execution error for '{}': {}", test.getName(), ex.getMessage());
            if (execution.getErrorMessage() == null) execution.fail("Request failed: " + ex.getMessage());
        }
        return executions.save(execution);
    }

    private String runTest(GeneratedTest test, BrowserContext context) {
        String type = test.getType() != null ? test.getType() : "smoke";
        return switch (type) {
            case "functional" -> executeFunctional(test.getTargetUrl(), context);
            case "auth"       -> executeAuth(test.getTargetUrl(), context);
            default           -> executeSmoke(test.getTargetUrl(), context);
        };
    }

    private String executeSmoke(String url, BrowserContext context) {
        com.microsoft.playwright.Page page = context.newPage();
        try {
            page.setDefaultNavigationTimeout(15000);
            Response response = page.navigate(url);
            if (response == null) return "No response from server";
            if (response.status() >= 400) return "HTTP " + response.status();

            String title = page.title();
            String titleLow = title != null ? title.toLowerCase() : "";
            if (titleLow.contains("not found") || titleLow.contains("404"))
                return "Page not found: " + title;
            if (titleLow.contains("500") || titleLow.contains("internal server error"))
                return "Server error page: " + title;

            String bodyText = (String) page.evaluate("document.body ? document.body.innerText : ''");
            if (bodyText == null || bodyText.trim().length() < 30)
                return "Page appears empty or has no content";

            return null;
        } catch (Exception e) {
            return "Navigation failed: " + e.getMessage();
        } finally {
            page.close();
        }
    }

    private String executeFunctional(String url, BrowserContext context) {
        com.microsoft.playwright.Page page = context.newPage();
        try {
            page.setDefaultNavigationTimeout(15000);
            Response response = page.navigate(url);
            if (response == null || response.status() >= 400)
                return "HTTP " + (response != null ? response.status() : "no response");

            Locator submitBtn = page.locator("button[type=submit], input[type=submit]").first();
            if (!submitBtn.isVisible()) return executeSmoke(url, context);

            try {
                submitBtn.click();
                page.waitForTimeout(1500);
            } catch (Exception ignored) {}

            String title = page.title();
            if (title != null && (title.contains("500") || title.toLowerCase().contains("server error")))
                return "Server error on empty form submission: " + title;

            return null;
        } catch (Exception e) {
            return "Navigation failed: " + e.getMessage();
        } finally {
            page.close();
        }
    }

    private String executeAuth(String url, BrowserContext context) {
        com.microsoft.playwright.Page page = context.newPage();
        try {
            page.setDefaultNavigationTimeout(15000);
            Response response = page.navigate(url);
            if (response == null || response.status() >= 400)
                return "HTTP " + (response != null ? response.status() : "no response");

            Locator emailInput = page.locator(
                    "input[type=email], input[name*=email i], input[name*=username i], input[placeholder*=email i]"
            ).first();
            if (emailInput.isVisible()) emailInput.fill("qaas.tester@test.com");

            Locator passwordInput = page.locator("input[type=password]").first();
            if (passwordInput.isVisible()) passwordInput.fill("TestPassword1!");

            Locator submitBtn = page.locator("button[type=submit], input[type=submit]").first();
            if (submitBtn.isVisible()) {
                submitBtn.click();
                page.waitForTimeout(2000);
            }

            String title = page.title();
            if (title != null && (title.contains("500") || title.toLowerCase().contains("server error")))
                return "Server error after auth attempt: " + title;

            return null;
        } catch (Exception e) {
            return "Navigation failed: " + e.getMessage();
        } finally {
            page.close();
        }
    }

    private Browser launchBrowser(Playwright pw) {
        return pw.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setChromiumSandbox(false)
                .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run")));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ExecutionDtos.ExecutionResponse> getByAnalysis(UUID analysisId, Pageable pageable) {
        var paged = executions.findByAnalysisId(analysisId, pageable);
        return PagedResponse.fromMapped(paged, paged.getContent().stream()
                .map(ExecutionDtos.ExecutionResponse::from).toList());
    }
}