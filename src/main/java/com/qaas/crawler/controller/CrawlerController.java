package com.qaas.crawler.controller;

import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.PageInfo;
import com.qaas.crawler.service.CrawlerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) { this.crawlerService = crawlerService; }

    @PostMapping("/start")
    public ResponseEntity<List<PageInfo>> start(@RequestParam String url, @RequestParam(defaultValue = "50") int maxPages) {
        return ResponseEntity.ok(crawlerService.crawl(url, new CrawlOptions(maxPages, null, null, null, java.util.List.of()), () -> false).pages());
    }
}
