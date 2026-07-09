package com.qaas.crawler.service.impl;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.CrawlResult;
import com.qaas.crawler.dto.PageInfo;
import com.qaas.crawler.service.CrawlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Service
public class CrawlerServiceImpl implements CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerServiceImpl.class);

    // JS that collects every navigable href on the page, including hash-fragment SPA routes
    private static final String EXTRACT_LINKS_JS = """
        Array.from(document.querySelectorAll('a[href]'))
             .map(a => a.href)
             .filter(h => h && h.length > 0)
        """;

    @Override
    public CrawlResult crawl(String baseUrl, CrawlOptions options) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        List<PageInfo> results = new ArrayList<>();
        queue.add(baseUrl);

        URI baseUri;
        try {
            baseUri = URI.create(baseUrl);
        } catch (Exception e) {
            log.warn("Invalid base URL: {}", baseUrl);
            return new CrawlResult(results, null);
        }

        String storageStateJson = null;

        try (Playwright pw = Playwright.create()) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChromiumSandbox(false)
                    .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run", "--no-default-browser-check"));
            Browser browser = pw.chromium().launch(opts);
            com.microsoft.playwright.Page page = browser.newPage();
            // Longer timeout: SPAs may need time for JS bundles to execute
            page.setDefaultNavigationTimeout(20000);

            if (options.hasAuth()) {
                doLogin(page, options);
                try {
                    storageStateJson = page.context().storageState();
                } catch (Exception e) {
                    log.warn("Could not capture storage state after login: {}", e.getMessage());
                }
            }

            while (!queue.isEmpty() && results.size() < options.maxPages()) {
                String url = queue.poll();
                if (visited.contains(url)) continue;
                if (isExcluded(url, options.excludedPatterns())) continue;
                visited.add(url);

                try {
                    // Wait for the full load event (not just HTML parse) so that
                    // client-side frameworks (React, Vue, Angular) have time to mount
                    // and render navigation links into the DOM.
                    Response response = page.navigate(url,
                            new com.microsoft.playwright.Page.NavigateOptions()
                                    .setWaitUntil(WaitUntilState.LOAD));
                    if (response == null || response.status() >= 400) continue;

                    // Extra settling time for SPAs that render asynchronously after load
                    try { page.waitForTimeout(1500); } catch (Exception ignored) {}

                    String title = page.title();
                    String html = page.content();
                    results.add(new PageInfo(url, title, html));

                    @SuppressWarnings("unchecked")
                    List<String> hrefs = (List<String>) page.evaluate(EXTRACT_LINKS_JS);
                    for (String href : hrefs) {
                        if (href == null || href.isBlank()
                                || href.startsWith("mailto:") || href.startsWith("javascript:")) continue;
                        try {
                            URI u = URI.create(href);
                            if (!baseUri.getHost().equalsIgnoreCase(u.getHost())) continue;

                            // Preserve hash fragments for hash-based SPA routing (e.g. /#/dashboard).
                            // Without this, every hash route normalises to "/" and gets skipped.
                            String fragment = (u.getFragment() != null && !u.getFragment().isBlank())
                                    ? "#" + u.getFragment() : "";
                            String path = (u.getPath() == null || u.getPath().isBlank()) ? "/" : u.getPath();
                            String normalized = u.getScheme() + "://" + u.getHost()
                                    + (u.getPort() > 0 ? ":" + u.getPort() : "")
                                    + path + fragment;

                            if (!visited.contains(normalized) && !isExcluded(normalized, options.excludedPatterns())) {
                                queue.add(normalized);
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    log.debug("Skipping {} — {}", url, e.getMessage());
                }
            }
            browser.close();
        } catch (Exception e) {
            log.error("Crawler failed for {}: {}", baseUrl, e.getMessage());
        }

        log.info("Crawled {} pages from {}", results.size(), baseUrl);
        return new CrawlResult(results, storageStateJson);
    }

    private void doLogin(com.microsoft.playwright.Page page, CrawlOptions opts) {
        try {
            page.navigate(opts.authUrl(), new com.microsoft.playwright.Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.LOAD)
                    .setTimeout(15000));

            Locator emailInput = page.locator(
                    "input[type=email], input[name*=email i], input[name*=username i], input[placeholder*=email i]"
            ).first();
            if (emailInput.isVisible()) emailInput.fill(opts.authUsername());

            Locator pwdInput = page.locator("input[type=password]").first();
            if (pwdInput.isVisible()) pwdInput.fill(opts.authPassword());

            Locator submitBtn = page.locator("button[type=submit], input[type=submit]").first();
            if (submitBtn.isVisible()) {
                submitBtn.click();
                page.waitForTimeout(3000);
            }
            log.info("Login completed at {}", opts.authUrl());
        } catch (Exception e) {
            log.warn("Login failed at {}: {}", opts.authUrl(), e.getMessage());
        }
    }

    private boolean isExcluded(String url, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        String lower = url.toLowerCase();
        return patterns.stream().anyMatch(p -> !p.isBlank() && lower.contains(p.trim().toLowerCase()));
    }
}