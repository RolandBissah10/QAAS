package com.qaas.crawler.dto;

public class PageInfo {
    private String url;
    private String title;
    private String htmlContent;

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
}