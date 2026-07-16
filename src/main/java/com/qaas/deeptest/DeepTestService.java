package com.qaas.deeptest;

import com.qaas.crawler.dto.PageInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DeepTestService {

    private static final Logger log = LoggerFactory.getLogger(DeepTestService.class);

    private static final List<String[]> SECURITY_HEADERS = List.of(
        // {header-name, category, title, description, severity}
        new String[]{"strict-transport-security", "SECURITY",
            "Missing HSTS header",
            "No Strict-Transport-Security header. Browsers cannot enforce HTTPS-only connections, leaving users vulnerable to downgrade attacks.",
            "HIGH"},
        new String[]{"content-security-policy", "SECURITY",
            "Missing Content-Security-Policy",
            "No CSP header found. Without CSP the page is significantly more vulnerable to XSS attacks.",
            "MEDIUM"},
        new String[]{"x-frame-options", "SECURITY",
            "Missing X-Frame-Options",
            "This page can be embedded in an iframe on any domain, enabling clickjacking attacks against users.",
            "MEDIUM"},
        new String[]{"x-content-type-options", "SECURITY",
            "Missing X-Content-Type-Options",
            "Browser may MIME-sniff responses away from the declared Content-Type, enabling content injection.",
            "LOW"},
        new String[]{"referrer-policy", "SECURITY",
            "Missing Referrer-Policy",
            "Full Referrer header is sent to external sites, potentially leaking sensitive URL parameters or paths.",
            "LOW"}
    );

    private final DeepFindingRepository repository;

    public DeepTestService(DeepFindingRepository repository) {
        this.repository = repository;
    }

    public void run(UUID analysisId, List<PageInfo> pages) {
        if (pages == null || pages.isEmpty()) return;
        log.info("Deep test starting for analysis {} — {} page(s)", analysisId, pages.size());

        Set<String> auditedHosts = new HashSet<>();
        for (PageInfo page : pages) {
            String host = extractHost(page.getUrl());
            if (host != null && auditedHosts.add(host)) {
                checkSecurityHeaders(analysisId, page);
            }
            checkConsoleErrors(analysisId, page);
            checkPerformance(analysisId, page);
            checkAccessibility(analysisId, page);
        }

        checkBrokenLinks(analysisId, pages);
        log.info("Deep test complete for analysis {}", analysisId);
    }

    // ── Security headers ──────────────────────────────────────────────────────

    private void checkSecurityHeaders(UUID analysisId, PageInfo page) {
        Map<String, String> headers = page.getResponseHeaders();
        if (headers == null || headers.isEmpty()) return;

        Set<String> present = headers.keySet().stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        for (String[] spec : SECURITY_HEADERS) {
            if (!present.contains(spec[0])) {
                save(analysisId, spec[1], spec[4], spec[2], spec[3], page.getUrl());
            }
        }
    }

    // ── Console errors ────────────────────────────────────────────────────────

    private void checkConsoleErrors(UUID analysisId, PageInfo page) {
        List<String> errors = page.getConsoleErrors();
        if (errors == null || errors.isEmpty()) return;
        for (String error : errors) {
            String msg = error.length() > 500 ? error.substring(0, 500) + "…" : error;
            save(analysisId, "CONSOLE_ERROR", "MEDIUM",
                "JavaScript error detected",
                msg, page.getUrl());
        }
    }

    // ── Performance ───────────────────────────────────────────────────────────

    private void checkPerformance(UUID analysisId, PageInfo page) {
        long ms = page.getLoadTimeMs();
        if (ms <= 0) return;
        if (ms > 8_000) {
            save(analysisId, "PERFORMANCE", "HIGH",
                "Very slow page load: " + ms + " ms",
                "The page took " + ms + " ms to fully load (threshold: 8 000 ms). Users will experience significant delays that may cause them to abandon the page.",
                page.getUrl());
        } else if (ms > 3_000) {
            save(analysisId, "PERFORMANCE", "MEDIUM",
                "Slow page load: " + ms + " ms",
                "The page took " + ms + " ms to load (threshold: 3 000 ms). Users will notice a noticeable delay.",
                page.getUrl());
        }
    }

    // ── Accessibility ─────────────────────────────────────────────────────────

    private void checkAccessibility(UUID analysisId, PageInfo page) {
        String html = page.getHtmlContent();
        if (html == null || html.isBlank()) return;
        try {
            Document doc = Jsoup.parse(html, page.getUrl());

            // Images missing alt attribute
            int missingAlt = doc.select("img:not([alt])").size();
            if (missingAlt > 0) {
                save(analysisId, "ACCESSIBILITY", "MEDIUM",
                    missingAlt + " image(s) missing alt attribute",
                    missingAlt + " <img> element(s) have no alt attribute. Screen readers cannot convey image content to visually impaired users.",
                    page.getUrl());
            }

            // Buttons with no accessible label
            long emptyButtons = doc.select("button").stream()
                .filter(b -> b.text().isBlank()
                    && b.attr("aria-label").isBlank()
                    && b.attr("aria-labelledby").isBlank()
                    && b.attr("title").isBlank())
                .count();
            if (emptyButtons > 0) {
                save(analysisId, "ACCESSIBILITY", "MEDIUM",
                    emptyButtons + " button(s) with no accessible label",
                    emptyButtons + " <button> element(s) have no visible text, aria-label, or title. Screen reader users cannot identify these controls.",
                    page.getUrl());
            }

            // Inputs missing label association
            long unlabeled = doc.select(
                    "input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]):not([type=image])"
                ).stream()
                .filter(input -> {
                    String id = input.attr("id");
                    if (!id.isBlank()) {
                        boolean hasLabel = doc.select("label").stream()
                            .anyMatch(l -> l.attr("for").equals(id));
                        if (hasLabel) return false;
                    }
                    return input.attr("aria-label").isBlank()
                        && input.attr("aria-labelledby").isBlank();
                })
                .count();
            if (unlabeled > 0) {
                save(analysisId, "ACCESSIBILITY", "LOW",
                    unlabeled + " form input(s) missing label",
                    unlabeled + " input(s) have no associated <label>, aria-label, or aria-labelledby. Screen readers cannot describe these fields.",
                    page.getUrl());
            }

            // Missing lang on <html>
            Element htmlEl = doc.selectFirst("html");
            if (htmlEl != null && htmlEl.attr("lang").isBlank()) {
                save(analysisId, "ACCESSIBILITY", "LOW",
                    "Missing lang attribute on <html>",
                    "The root <html> element has no lang attribute. Assistive technologies cannot automatically select the correct language for text-to-speech.",
                    page.getUrl());
            }

        } catch (Exception e) {
            log.debug("Accessibility check failed for {}: {}", page.getUrl(), e.getMessage());
        }
    }

    // ── Broken links ──────────────────────────────────────────────────────────

    private void checkBrokenLinks(UUID analysisId, List<PageInfo> pages) {
        String baseHost = extractHost(pages.get(0).getUrl());
        if (baseHost == null) return;

        // Collect up to 40 unique same-host links (strip fragment, deduplicate)
        Set<String> links = new LinkedHashSet<>();
        outer:
        for (PageInfo page : pages) {
            if (page.getHtmlContent() == null) continue;
            try {
                Document doc = Jsoup.parse(page.getHtmlContent(), page.getUrl());
                for (Element a : doc.select("a[href]")) {
                    String abs = a.absUrl("href");
                    if (abs.isBlank() || abs.startsWith("mailto:") || abs.startsWith("javascript:")) continue;
                    String stripped = abs.contains("#") ? abs.substring(0, abs.indexOf('#')) : abs;
                    if (stripped.isBlank()) continue;
                    try {
                        URI u = URI.create(stripped);
                        if (baseHost.equalsIgnoreCase(u.getHost())) {
                            links.add(stripped);
                            if (links.size() >= 40) break outer;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log.debug("Link extraction failed for {}: {}", page.getUrl(), e.getMessage());
            }
        }

        if (links.isEmpty()) return;
        log.info("Checking {} link(s) for analysis {}", links.size(), analysisId);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        // Fire all HEAD requests concurrently, wait at most 8 seconds for all
        List<CompletableFuture<Void>> futures = links.stream().map(link -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(link))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(4))
                    .build();
                return client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .orTimeout(5, TimeUnit.SECONDS)
                    .<Void>handle((resp, ex) -> {
                        if (ex != null) return null;
                        int status = resp.statusCode();
                        if (status == 401 || status == 403) return null; // auth-protected, not broken
                        if (status >= 400 && status < 500) {
                            save(analysisId, "BROKEN_LINK", "MEDIUM",
                                "Broken link returns HTTP " + status,
                                "The link " + link + " returned HTTP " + status + ". Users clicking this link will see an error page.",
                                link);
                        } else if (status >= 500) {
                            save(analysisId, "BROKEN_LINK", "HIGH",
                                "Link causes server error: HTTP " + status,
                                "The link " + link + " returned HTTP " + status + ". The destination page is failing on the server.",
                                link);
                        }
                        return null;
                    });
            } catch (Exception e) {
                log.debug("Link check setup failed for {}: {}", link, e.getMessage());
                return CompletableFuture.<Void>completedFuture(null);
            }
        }).collect(Collectors.toList());

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Broken link check timed out or was interrupted: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void save(UUID analysisId, String category, String severity,
                      String title, String description, String pageUrl) {
        try {
            repository.save(new DeepFinding(analysisId, category, severity, title, description, pageUrl));
        } catch (Exception e) {
            log.warn("Failed to save deep finding [{}] {}: {}", category, title, e.getMessage());
        }
    }

    private String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}