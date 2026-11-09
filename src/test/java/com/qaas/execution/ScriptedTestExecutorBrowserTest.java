package com.qaas.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptedTestExecutorBrowserTest {

    @Test
    void executesGeneratedStepsInChromiumAgainstAnIsolatedPage() {
        String targetUrl = "https://qaas.test/";
        String script = """
                [
                  {"action":"fill","selector":"#name","value":"QAAS"},
                  {"action":"click","selector":"#submit"},
                  {"action":"assertText","selector":"#result","contains":"Hello, QAAS"},
                  {"action":"assertUrl","contains":"qaas.test"}
                ]
                """;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchChromium(playwright);
            try {
                var context = browser.newContext();
                context.route("https://qaas.test/**", route -> route.fulfill(
                        new com.microsoft.playwright.Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("""
                                        <!doctype html>
                                        <html>
                                          <body>
                                            <input id="name">
                                            <button id="submit" onclick="document.querySelector('#result').textContent = 'Hello, ' + document.querySelector('#name').value">Submit</button>
                                            <p id="result"></p>
                                          </body>
                                        </html>
                                        """)));

                String failure = new ScriptedTestExecutor(new ObjectMapper())
                        .execute(script, targetUrl, context);

                assertThat(failure).isNull();
                context.close();
            } finally {
                browser.close();
            }
        }
    }

    @Test
    void reportsTheFailingStepInsteadOfPassingTheScript() {
        String targetUrl = "https://qaas.test/";
        String script = """
                [{"action":"assertText","selector":"#result","contains":"expected"}]
                """;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchChromium(playwright);
            try {
                var context = browser.newContext();
                context.route("https://qaas.test/**", route -> route.fulfill(
                        new com.microsoft.playwright.Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("<html><body><p id='result'>actual</p></body></html>")));

                String failure = new ScriptedTestExecutor(new ObjectMapper())
                        .execute(script, targetUrl, context);

                assertThat(failure).contains("assertText failed", "expected");
                context.close();
            } finally {
                browser.close();
            }
        }
    }

    @Test
    void rejectsScriptedNavigationOutsideTheApplicationOrigin() {
        String targetUrl = "https://qaas.test/";
        String script = """
                [{"action":"navigate","value":"https://unrelated.example.test/account"}]
                """;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchChromium(playwright);
            try {
                var context = browser.newContext();
                context.route("https://qaas.test/**", route -> route.fulfill(
                        new com.microsoft.playwright.Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("<html><body>Application home</body></html>")));

                String failure = new ScriptedTestExecutor(new ObjectMapper())
                        .execute(script, targetUrl, context);

                assertThat(failure).contains("outside the target application origin");
                context.close();
            } finally {
                browser.close();
            }
        }
    }

    @Test
    void executesJourneyAcrossOnlyItsDiscoveredApplicationPages() {
        String targetUrl = "https://qaas.test/";
        String script = """
                {
                  "allowedUrls": ["https://qaas.test/", "https://qaas.test/alerts"],
                  "steps": [
                    {"action":"click","selector":"a[href='/alerts']"},
                    {"action":"assertVisible","selector":"form"}
                  ]
                }
                """;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchChromium(playwright);
            try {
                var context = browser.newContext();
                context.route("https://qaas.test/**", route -> {
                    if (route.request().url().endsWith("/alerts")) {
                        route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("<html><body><form><input name='alert'></form></body></html>"));
                    } else {
                        route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("<html><body><a href='/alerts'>Create alert</a></body></html>"));
                    }
                });

                String failure = new ScriptedTestExecutor(new ObjectMapper())
                        .execute(script, targetUrl, context);

                assertThat(failure).isNull();
                context.close();
            } finally {
                browser.close();
            }
        }
    }

    @Test
    void rejectsJourneyNavigationToUndiscoveredSameOriginPage() {
        String targetUrl = "https://qaas.test/";
        String script = """
                {
                  "allowedUrls": ["https://qaas.test/"],
                  "steps": [{"action":"click","selector":"a[href='/unlisted']"}]
                }
                """;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchChromium(playwright);
            try {
                var context = browser.newContext();
                context.route("https://qaas.test/**", route -> route.fulfill(
                        new com.microsoft.playwright.Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("<html><body><a href='/unlisted'>Unlisted</a></body></html>")));

                String failure = new ScriptedTestExecutor(new ObjectMapper())
                        .execute(script, targetUrl, context);

                assertThat(failure).contains("not discovered");
                context.close();
            } finally {
                browser.close();
            }
        }
    }

    private Browser launchChromium(Playwright playwright) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setChromiumSandbox(false)
                .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run"));
        String executablePath = System.getProperty("playwright.chromium.path",
                System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH"));
        if (executablePath != null && !executablePath.isBlank()) {
            options.setExecutablePath(Path.of(executablePath));
        }
        return playwright.chromium().launch(options);
    }
}
