package com.qaas.crawler.dto;

import java.util.List;

public record CrawlOptions(
        int maxPages,
        String authUrl,
        String authUsername,
        String authPassword,
        List<String> excludedPatterns
) {
    public static CrawlOptions defaults() {
        return new CrawlOptions(20, null, null, null, List.of());
    }

    public boolean hasAuth() {
        return authUrl != null && !authUrl.isBlank()
                && authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank();
    }
}