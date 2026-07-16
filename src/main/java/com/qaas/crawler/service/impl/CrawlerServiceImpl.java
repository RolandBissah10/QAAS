package com.qaas.crawler.service.impl;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.qaas.crawler.dto.ApiEndpointInfo;
import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.CrawlResult;
import com.qaas.crawler.dto.PageInfo;
import com.qaas.crawler.service.CrawlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

@Service
public class CrawlerServiceImpl implements CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerServiceImpl.class);

    private static final String EXTRACT_LINKS_JS = """
        Array.from(document.querySelectorAll('a[href]'))
             .map(a => a.href)
             .filter(h => h && h.length > 0)
        """;

    private static final List<String> COMMON_PATHS = List.of(
        "/dashboard", "/home", "/profile", "/settings", "/account",
        "/admin", "/analytics", "/reports", "/users", "/products",
        "/orders", "/notifications", "/messages", "/search",
        "/help", "/about", "/contact", "/projects", "/teams",
        "/invoices", "/billing", "/integrations", "/api-keys"
    );

    private static final Pattern SITEMAP_LOC = Pattern.compile("<loc>([^<]+)</loc>", Pattern.CASE_INSENSITIVE);

    @Override
    public CrawlResult crawl(String baseUrl, CrawlOptions options, Supplier<Boolean> cancelChecker) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        List<PageInfo> results = new ArrayList<>();
        List<ApiEndpointInfo> capturedApiEndpoints = new ArrayList<>();
        Set<String> seenApiKeys = new HashSet<>();
        queue.add(baseUrl);

        URI baseUri;
        try {
            baseUri = URI.create(baseUrl);
        } catch (Exception e) {
            log.warn("Invalid base URL: {}", baseUrl);
            return new CrawlResult(results, null, List.of());
        }

        String storageStateJson = null;

        try (Playwright pw = Playwright.create()) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChromiumSandbox(false)
                    .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run", "--no-default-browser-check"));
            Browser browser = pw.chromium().launch(opts);
            com.microsoft.playwright.Page page = browser.newPage();
            page.setDefaultNavigationTimeout(20000);

            // Collect console errors per page; cleared before each navigation
            List<String> pageConsoleErrors = new ArrayList<>();
            page.onConsoleMessage(msg -> {
                if ("error".equals(msg.type())) {
                    synchronized (pageConsoleErrors) { pageConsoleErrors.add(msg.text()); }
                }
            });
            page.onPageError(error -> {
                synchronized (pageConsoleErrors) {
                    pageConsoleErrors.add("Uncaught exception: " + error);
                }
            });

            page.onResponse(response -> {
                try {
                    String rUrl = response.url();
                    String method = response.request().method();
                    if (isApiEndpoint(rUrl, response, baseUri)) {
                        String key = method + ":" + normalizeApiUrl(rUrl);
                        if (seenApiKeys.add(key)) {
                            capturedApiEndpoints.add(new ApiEndpointInfo(method, rUrl, response.status()));
                        }
                    }
                } catch (Exception ignored) {}
            });

            if (options.hasAuth()) {
                doLogin(page, options);
                try {
                    storageStateJson = page.context().storageState();
                } catch (Exception e) {
                    log.warn("Could not capture storage state after login: {}", e.getMessage());
                }
            }

            List<String> sitemapUrls = extractFromSitemap(page, baseUri);
            sitemapUrls.stream()
                    .filter(u -> !visited.contains(u) && !isExcluded(u, options.excludedPatterns()))
                    .forEach(queue::add);
            if (!sitemapUrls.isEmpty()) {
                log.info("Seeded {} URLs from sitemap.xml", sitemapUrls.size());
                try {
                    page.navigate(baseUrl, new com.microsoft.playwright.Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.LOAD).setTimeout(15000));
                } catch (Exception ignored) {}
            }

            // BFS crawl — exits early if cancellation is requested
            while (!queue.isEmpty() && results.size() < options.maxPages()) {
                if (Boolean.TRUE.equals(cancelChecker.get())) {
                    log.info("Crawl cancelled — stopping BFS at {} pages visited", results.size());
                    break;
                }

                String url = queue.poll();
                if (visited.contains(url)) continue;
                if (isExcluded(url, options.excludedPatterns())) continue;
                visited.add(url);

                try {
                    synchronized (pageConsoleErrors) { pageConsoleErrors.clear(); }
                    long navStart = System.currentTimeMillis();

                    Response response = page.navigate(url,
                            new com.microsoft.playwright.Page.NavigateOptions()
                                    .setWaitUntil(WaitUntilState.LOAD));
                    if (response == null || response.status() >= 400) continue;

                    String finalUrl = page.url();

                    if (!sameHost(finalUrl, baseUri)) {
                        log.debug("Skipping {} — redirected off-domain to {}", url, finalUrl);
                        continue;
                    }

                    if (isAuthRedirect(url, finalUrl)) {
                        log.debug("Skipping {} — redirected to auth page ({})", url, finalUrl);
                        visited.add(finalUrl);
                        continue;
                    }
                    visited.add(finalUrl);

                    try { page.waitForTimeout(1500); } catch (Exception ignored) {}
                    long loadTimeMs = System.currentTimeMillis() - navStart;

                    String title = page.title();
                    String html = page.content();

                    List<String> errors;
                    synchronized (pageConsoleErrors) { errors = new ArrayList<>(pageConsoleErrors); }
                    Map<String, String> headers;
                    try { headers = new java.util.HashMap<>(response.headers()); }
                    catch (Exception ignored) { headers = Map.of(); }

                    PageInfo pageInfo = new PageInfo(url, title, html);
                    pageInfo.setConsoleErrors(errors);
                    pageInfo.setResponseHeaders(headers);
                    pageInfo.setLoadTimeMs(loadTimeMs);
                    results.add(pageInfo);

                    @SuppressWarnings("unchecked")
                    List<String> hrefs = (List<String>) page.evaluate(EXTRACT_LINKS_JS);
                    for (String href : hrefs) {
                        if (href == null || href.isBlank()
                                || href.startsWith("mailto:") || href.startsWith("javascript:")) continue;
                        try {
                            URI u = URI.create(href);
                            if (!baseUri.getHost().equalsIgnoreCase(u.getHost())) continue;

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

            // Only probe common paths if not cancelled
            if (!Boolean.TRUE.equals(cancelChecker.get())) {
                probeCommonPaths(page, baseUri, visited, results, options, cancelChecker, pageConsoleErrors);
            }

            browser.close();
        } catch (Exception e) {
            log.error("Crawler failed for {}: {}", baseUrl, e.getMessage());
        }

        log.info("Crawled {} pages and {} API endpoints from {}",
                results.size(), capturedApiEndpoints.size(), baseUrl);
        return new CrawlResult(results, storageStateJson, capturedApiEndpoints);
    }

    // ── Sitemap extraction ────────────────────────────────────────────────────

    private List<String> extractFromSitemap(com.microsoft.playwright.Page page, URI baseUri) {
        List<String> urls = new ArrayList<>();
        String sitemapUrl = baseUri.getScheme() + "://" + baseUri.getHost()
                + (baseUri.getPort() > 0 ? ":" + baseUri.getPort() : "") + "/sitemap.xml";
        try {
            Response resp = page.navigate(sitemapUrl, new com.microsoft.playwright.Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.LOAD).setTimeout(8000));
            if (resp != null && resp.ok()) {
                String content = page.content();
                Matcher m = SITEMAP_LOC.matcher(content);
                while (m.find()) {
                    String loc = m.group(1).trim();
                    try {
                        URI u = URI.create(loc);
                        if (baseUri.getHost().equalsIgnoreCase(u.getHost())) {
                            urls.add(loc);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.debug("No sitemap.xml at {}: {}", sitemapUrl, e.getMessage());
        }
        return urls;
    }

    // ── Common path probing ───────────────────────────────────────────────────

    private void probeCommonPaths(com.microsoft.playwright.Page page, URI baseUri,
                                   Set<String> visited, List<PageInfo> results,
                                   CrawlOptions options, Supplier<Boolean> cancelChecker,
                                   List<String> pageConsoleErrors) {
        String base = baseUri.getScheme() + "://" + baseUri.getHost()
                + (baseUri.getPort() > 0 ? ":" + baseUri.getPort() : "");

        for (String path : COMMON_PATHS) {
            if (results.size() >= options.maxPages()) break;
            if (Boolean.TRUE.equals(cancelChecker.get())) break;

            String url = base + path;
            if (visited.contains(url) || isExcluded(url, options.excludedPatterns())) continue;
            visited.add(url);

            try {
                synchronized (pageConsoleErrors) { pageConsoleErrors.clear(); }
                long navStart = System.currentTimeMillis();

                Response response = page.navigate(url, new com.microsoft.playwright.Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.LOAD).setTimeout(10000));
                if (response == null || response.status() >= 400) continue;

                String finalUrl = page.url();

                if (!sameHost(finalUrl, baseUri)) {
                    log.debug("Common path {} redirected off-domain to {} — skipping", url, finalUrl);
                    continue;
                }

                if (isAuthRedirect(url, finalUrl)) {
                    visited.add(finalUrl);
                    continue;
                }
                visited.add(finalUrl);

                try { page.waitForTimeout(1000); } catch (Exception ignored) {}
                long loadTimeMs = System.currentTimeMillis() - navStart;

                String title = page.title();
                String html = page.content();

                List<String> errors;
                synchronized (pageConsoleErrors) { errors = new ArrayList<>(pageConsoleErrors); }
                Map<String, String> headers;
                try { headers = new java.util.HashMap<>(response.headers()); }
                catch (Exception ignored) { headers = Map.of(); }

                PageInfo pageInfo = new PageInfo(url, title, html);
                pageInfo.setConsoleErrors(errors);
                pageInfo.setResponseHeaders(headers);
                pageInfo.setLoadTimeMs(loadTimeMs);
                results.add(pageInfo);
                log.debug("Common path discovered: {}", url);
            } catch (Exception e) {
                log.debug("Common path probe failed for {}: {}", url, e.getMessage());
            }
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

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

    // ── API endpoint detection ────────────────────────────────────────────────

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
            ".woff", ".woff2", ".ttf", ".eot", ".map", ".webp", ".avif",
            ".pdf", ".zip", ".mp4", ".mp3");

    private boolean isApiEndpoint(String url, Response response, URI baseUri) {
        try {
            URI u = URI.create(url);
            if (!baseUri.getHost().equalsIgnoreCase(u.getHost())) return false;
            if (isStaticAsset(url)) return false;

            try {
                Map<String, String> headers = response.headers();
                if (headers != null) {
                    String ct = headers.getOrDefault("content-type", "");
                    if (ct.contains("application/json")
                            || ct.contains("application/xml")
                            || ct.contains("text/xml")
                            || ct.contains("application/graphql")) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}

            String path = u.getPath() == null ? "" : u.getPath().toLowerCase();
            return path.startsWith("/api/") || path.startsWith("/v1/")
                    || path.startsWith("/v2/") || path.startsWith("/v3/")
                    || path.startsWith("/rest/") || path.startsWith("/graphql")
                    || path.startsWith("/query") || path.startsWith("/gql");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStaticAsset(String url) {
        String lower = url.toLowerCase();
        int q = lower.indexOf('?');
        String path = q >= 0 ? lower.substring(0, q) : lower;
        return STATIC_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private String normalizeApiUrl(String url) {
        try {
            URI u = URI.create(url);
            return u.getScheme() + "://" + u.getHost()
                    + (u.getPort() > 0 ? ":" + u.getPort() : "")
                    + (u.getPath() == null ? "/" : u.getPath());
        } catch (Exception e) {
            return url;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean sameHost(String url, URI baseUri) {
        try {
            return baseUri.getHost().equalsIgnoreCase(URI.create(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAuthRedirect(String requestedUrl, String finalUrl) {
        if (finalUrl == null || finalUrl.equals(requestedUrl)) return false;
        String lower = finalUrl.toLowerCase();
        return lower.contains("login") || lower.contains("signin")
                || lower.contains("/auth") || lower.contains("unauthorized");
    }

    private boolean isExcluded(String url, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        String lower = url.toLowerCase();
        return patterns.stream().anyMatch(p -> !p.isBlank() && lower.contains(p.trim().toLowerCase()));
    }
}