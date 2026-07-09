package com.qaas.playwright.service.impl;

import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.PageInfo;
import com.qaas.crawler.service.CrawlerService;
import com.qaas.playwright.service.PlaywrightCrawlerService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaywrightCrawlerServiceImpl implements PlaywrightCrawlerService {

    private final CrawlerService crawlerService;

    public PlaywrightCrawlerServiceImpl(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @Override
    public List<PageInfo> crawl(String baseUrl, int maxPages) throws Exception {
        return crawlerService.crawl(baseUrl, new CrawlOptions(maxPages, null, null, null, List.of()));
    }
}