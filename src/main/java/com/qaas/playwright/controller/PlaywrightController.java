package com.qaas.playwright.controller;

import com.qaas.crawler.dto.PageInfo;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.playwright.service.PlaywrightCrawlerService;
import com.qaas.playwright.service.PlaywrightTestRunnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/playwright")
public class PlaywrightController {

    private final PlaywrightCrawlerService crawlerService;
    private final PlaywrightTestRunnerService runnerService;

    public PlaywrightController(PlaywrightCrawlerService crawlerService, PlaywrightTestRunnerService runnerService) {
        this.crawlerService = crawlerService;
        this.runnerService = runnerService;
    }

    @PostMapping("/crawl")
    public ResponseEntity<List<PageInfo>> crawl(@RequestParam String url, @RequestParam(defaultValue = "50") int maxPages) throws Exception {
        return ResponseEntity.ok(crawlerService.crawl(url, maxPages));
    }

    @PostMapping("/run-tests/{analysisId}")
    public ResponseEntity<List<GeneratedTest>> runTests(@PathVariable UUID analysisId) throws Exception {
        return ResponseEntity.ok(runnerService.runTestsForAnalysis(analysisId));
    }

    @PostMapping("/run-tests")
    public ResponseEntity<List<GeneratedTest>> runAllTests() throws Exception {
        return ResponseEntity.ok(runnerService.runAllTests());
    }
}
