package com.qaas.crawler.service;

import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.PageInfo;

import java.util.List;

public interface CrawlerService {
    List<PageInfo> crawl(String baseUrl, CrawlOptions options);
}