package com.qaas.crawler.service.impl;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.qaas.common.ApplicationUrlScope;
import com.qaas.crawler.dto.ApiEndpointInfo;
import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.CrawlResult;
import com.qaas.crawler.dto.PageInfo;
import com.qaas.crawler.service.CrawlerService;
import com.qaas.exception.BadRequestException;
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

    private static final Pattern SITEMAP_LOC = Pattern.compile("<loc>([^<]+)</loc>", Pattern.CASE_INSENSITIVE);

    @Override
    public CrawlResult crawl(String baseUrl, CrawlOptions options, Supplier<Boolean> cancelChecker) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        List<PageInfo> results = new ArrayList<>();
        List<ApiEndpointInfo> capturedApiEndpoints = new ArrayList<>();
        Set<String> seenApiKeys = new HashSet<>();

        URI baseUri;
        try {
            baseUri = URI.create(baseUrl);
        } catch (Exception e) {
            throw new BadRequestException("Invalid crawler URL");
        }
        CrawlTargetValidator.validate(baseUrl);
        queue.add(baseUrl);

        String storageStateJson = null;

        try (Playwright pw = Playwright.create()) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChromiumSandbox(false)
                    .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run", "--no-default-browser-check"));
            Browser browser = pw.chromium().launch(opts);
            com.microsoft.playwright.Page page = browser.newPage();
            page.route("**/*", route -> {
                try {
                    CrawlTargetValidator.validate(route.request().url());
                    if (route.request().isNavigationRequest()
                            && route.request().frame().parentFrame() == null
                            && !ApplicationUrlScope.isSameOrigin(baseUrl, route.request().url())) {
                        log.debug("Blocked crawler navigation outside the target application origin");
                        route.abort();
                        return;
                    }
                    route.resume();
                } catch (BadRequestException e) {
                    log.warn("Blocked crawler request to a non-public or unsupported destination");
                    route.abort();
                }
            });
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
                CrawlTargetValidator.validate(options.authUrl());
                if (!doLogin(page, options)) {
                    throw new IllegalStateException(
                            "Configured application login failed; verify the project login URL and credentials.");
                }
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
                            if (!ApplicationUrlScope.isSameOrigin(baseUri.toString(), href)) continue;

                            String path = (u.getRawPath() == null || u.getRawPath().isBlank())
                                    ? "/" : u.getRawPath();
                            String query = u.getRawQuery() == null ? "" : "?" + u.getRawQuery();
                            String normalized = u.getScheme() + "://" + u.getHost()
                                    + (u.getPort() > 0 ? ":" + u.getPort() : "")
                                    + path + query;

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
                        if (ApplicationUrlScope.isSameOrigin(baseUri.toString(), loc)) {
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

    // ── Login ─────────────────────────────────────────────────────────────────

    boolean doLogin(com.microsoft.playwright.Page page, CrawlOptions opts) {
        try {
            Exception navigationFailure = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    page.navigate(opts.authUrl(), new com.microsoft.playwright.Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(20000));
                    navigationFailure = null;
                    break;
                } catch (Exception e) {
                    navigationFailure = e;
                    if (attempt < 2) page.waitForTimeout(500);
                }
            }
            if (navigationFailure != null) throw navigationFailure;

            Locator emailInput = page.locator(
                    "input[type=email]:visible, input[name*=email i]:visible, "
                            + "input[name*=username i]:visible, input[placeholder*=email i]:visible, "
                            + "form input[type=text]:visible"
            ).first();
            emailInput.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(15000));
            emailInput.fill(opts.authUsername());

            Locator pwdInput = page.locator(
                    "form input[type=password]:visible, input[type=password]:visible"
            ).first();
            pwdInput.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));
            pwdInput.fill(opts.authPassword());

            Locator submitBtn = page.locator("form button[type=submit], form input[type=submit]").first();
            if (!submitBtn.isVisible()) submitBtn = page.locator("form button:visible").last();
            if (submitBtn.isVisible()) {
                submitBtn.click();
            } else {
                pwdInput.press("Enter");
            }
            long loginDeadline = System.currentTimeMillis() + 90_000;
            boolean authenticated = false;
            String loginError = null;
            while (System.currentTimeMillis() < loginDeadline) {
                if (hasLoginToken(page)
                        || isVisible(page, ".alert-success:visible")
                        || !page.url().equals(opts.authUrl())) {
                    authenticated = true;
                    break;
                }

                loginError = visibleText(page, ".alert-error:visible");
                if (loginError != null) break;
                page.waitForTimeout(250);
            }

            if (!authenticated) {
                if (loginError != null) {
                    loginError = loginError
                            .replace(opts.authUsername(), "[configured username]")
                            .replace(opts.authPassword(), "[configured password]");
                    log.warn("Application rejected login at {}: {}", opts.authUrl(),
                            loginError.length() > 200 ? loginError.substring(0, 200) : loginError);
                } else {
                    log.warn("Login was not confirmed within 90 seconds at {}", opts.authUrl());
                }
                return false;
            }

            log.info("Login succeeded at {}", opts.authUrl());
            return true;
        } catch (Exception e) {
            log.warn("Login failed at {}: {}", opts.authUrl(), e.getMessage());
            return false;
        }
    }

    private boolean hasLoginToken(com.microsoft.playwright.Page page) {
        return Boolean.TRUE.equals(page.evaluate(
                "() => Boolean(localStorage.getItem('token') || localStorage.getItem('access_token'))"));
    }

    private boolean isVisible(com.microsoft.playwright.Page page, String selector) {
        return page.locator(selector).first().isVisible();
    }

    private String visibleText(com.microsoft.playwright.Page page, String selector) {
        Locator alert = page.locator(selector).first();
        if (!alert.isVisible()) return null;
        String text = alert.innerText().trim();
        return text.isEmpty() ? null : text;
    }

    // ── API endpoint detection ────────────────────────────────────────────────

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
            ".woff", ".woff2", ".ttf", ".eot", ".map", ".webp", ".avif",
            ".pdf", ".zip", ".mp4", ".mp3");

    private boolean isApiEndpoint(String url, Response response, URI baseUri) {
        try {
            URI u = URI.create(url);
            // Accept same host or any subdomain of the base domain
            if (!ApplicationUrlScope.isSameOrigin(baseUri.toString(), url)) return false;
            if (isStaticAsset(url)) return false;

            // Non-GET/HEAD/OPTIONS methods are almost always API calls
            String method = response.request().method().toUpperCase();
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
                return true;
            }

            // Content-Type based detection
            try {
                Map<String, String> headers = response.headers();
                if (headers != null) {
                    String ct = headers.getOrDefault("content-type", "");
                    if (ct.contains("application/json")
                            || ct.contains("application/xml")
                            || ct.contains("text/xml")
                            || ct.contains("application/graphql")
                            || ct.contains("application/ld+json")) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}

            // Path-based detection — catches both top-level and nested API paths
            String path = u.getPath() == null ? "" : u.getPath().toLowerCase();
            return path.startsWith("/api/") || path.contains("/api/")
                    || path.startsWith("/v1/") || path.startsWith("/v2/")
                    || path.startsWith("/v3/") || path.startsWith("/v4/")
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
        return ApplicationUrlScope.isSameOrigin(baseUri.toString(), url);
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