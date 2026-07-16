package com.qaas.crawler.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageInfo {
    private String url;
    private String title;
    private String htmlContent;
    /** JS console errors and uncaught exceptions captured during the page load. */
    private List<String> consoleErrors = new ArrayList<>();
    /** HTTP response headers from the main navigation request. */
    private Map<String, String> responseHeaders = new HashMap<>();
    /** Total time in ms from navigation start to page settle (including waitForTimeout). */
    private long loadTimeMs;

    public PageInfo() {}

    public PageInfo(String url, String title) {
        this.url = url;
        this.title = title;
    }

    public PageInfo(String url, String title, String htmlContent) {
        this.url = url;
        this.title = title;
        this.htmlContent = htmlContent;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
    public List<String> getConsoleErrors() { return consoleErrors; }
    public void setConsoleErrors(List<String> consoleErrors) { this.consoleErrors = consoleErrors; }
    public Map<String, String> getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(Map<String, String> responseHeaders) { this.responseHeaders = responseHeaders; }
    public long getLoadTimeMs() { return loadTimeMs; }
    public void setLoadTimeMs(long loadTimeMs) { this.loadTimeMs = loadTimeMs; }
}