package com.qaas.crawler.service;

import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.CrawlResult;

import java.util.function.Supplier;

public interface CrawlerService {
    CrawlResult crawl(String baseUrl, CrawlOptions options, Supplier<Boolean> cancelChecker);
}