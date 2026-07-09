package com.qaas.crawler.service;

import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.CrawlResult;

public interface CrawlerService {
    CrawlResult crawl(String baseUrl, CrawlOptions options);
}