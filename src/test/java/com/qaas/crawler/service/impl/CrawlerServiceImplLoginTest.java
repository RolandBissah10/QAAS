package com.qaas.crawler.service.impl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.qaas.crawler.dto.CrawlOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerServiceImplLoginTest {

    @Test
    void waitsForHydratedLoginFormAndSubmitsAnUnmarkedButton() {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChromiumSandbox(false)
                    .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run"));
            String executablePath = System.getProperty("playwright.chromium.path",
                    System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH"));
            if (executablePath != null && !executablePath.isBlank()) {
                launchOptions.setExecutablePath(Path.of(executablePath));
            }

            Browser browser = playwright.chromium().launch(launchOptions);
            try {
                BrowserContext context = browser.newContext();
                context.route("https://qaas.test/**", route -> {
                    if (route.request().url().endsWith("/login")) {
                        route.fulfill(new Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("""
                                        <!doctype html>
                                        <html><body><div id="root"></div>
                                          <script>
                                            setTimeout(() => {
                                              document.querySelector('#root').innerHTML =
                                                '<form><input type="email"><input type="password"><button>Sign in</button></form>';
                                              document.querySelector('form').addEventListener('submit', event => {
                                                event.preventDefault();
                                                const button = document.querySelector('form button');
                                                button.disabled = true;
                                                setTimeout(() => {
                                                  button.disabled = false;
                                                  localStorage.setItem('token', 'test-session');
                                                  const success = document.createElement('p');
                                                  success.className = 'alert-success';
                                                  success.textContent = 'Login successful! Redirecting to dashboard...';
                                                  document.querySelector('#root').appendChild(success);
                                                  setTimeout(() => {
                                                    document.querySelector('#root').innerHTML = '<h1>Dashboard</h1>';
                                                    history.pushState({}, '', '/dashboard');
                                                  }, 2000);
                                                }, 1000);
                                              });
                                            }, 500);
                                          </script>
                                        </body></html>
                                        """));
                    } else {
                        route.fulfill(new Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("<html><body><h1>Dashboard</h1></body></html>"));
                    }
                });

                var page = context.newPage();
                boolean loggedIn = new CrawlerServiceImpl().doLogin(page,
                        new CrawlOptions(20, "https://qaas.test/login",
                                "tester@example.test", "safe-test-password", List.of()));

                assertThat(loggedIn).isTrue();
                assertThat(page.evaluate("() => localStorage.getItem('token')")).isEqualTo("test-session");
                context.close();
            } finally {
                browser.close();
            }
        }
    }
}
