package com.qaas.playwright.service;

import com.qaas.crawler.dto.PageInfo;

import java.util.List;

public interface PlaywrightCrawlerService {
    List<PageInfo> crawl(String baseUrl, int maxPages) throws Exception;
}
