package com.qaas.analysis.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.qaas.analysis.ProgressEmitterRegistry;
import com.qaas.analysis.dto.ProgressEvent;
import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.bug.BugDetectionService;
import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.PageInfo;
import com.qaas.crawler.service.CrawlerService;
import com.qaas.project.settings.ProjectSettingsRepository;
import com.qaas.discovery.service.UIElementDiscoveryService;
import com.qaas.execution.ExecutionService;
import com.qaas.execution.TestExecution;
import com.qaas.generator.entity.GeneratedTest;
import com.qaas.generator.service.TestGenerationService;
import com.qaas.page.entity.Page;
import com.qaas.page.repository.PageRepository;
import com.qaas.notification.AnalysisNotificationEvent;
import com.qaas.report.ReportFormat;
import com.qaas.report.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class AnalysisPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipelineService.class);

    private final AnalysisRepository analysisRepository;
    private final CrawlerService crawlerService;
    private final PageRepository pageRepository;
    private final UIElementDiscoveryService uiElementDiscoveryService;
    private final TestGenerationService testGenerationService;
    private final ExecutionService executionService;
    private final BugDetectionService bugDetectionService;
    private final ReportService reportService;
    private final ProgressEmitterRegistry progress;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AnalysisPipelineService(AnalysisRepository analysisRepository,
                                   CrawlerService crawlerService,
                                   PageRepository pageRepository,
                                   UIElementDiscoveryService uiElementDiscoveryService,
                                   TestGenerationService testGenerationService,
                                   ExecutionService executionService,
                                   BugDetectionService bugDetectionService,
                                   ReportService reportService,
                                   ProgressEmitterRegistry progress,
                                   ProjectSettingsRepository projectSettingsRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.analysisRepository = analysisRepository;
        this.crawlerService = crawlerService;
        this.pageRepository = pageRepository;
        this.uiElementDiscoveryService = uiElementDiscoveryService;
        this.testGenerationService = testGenerationService;
        this.executionService = executionService;
        this.bugDetectionService = bugDetectionService;
        this.reportService = reportService;
        this.progress = progress;
        this.projectSettingsRepository = projectSettingsRepository;
        this.eventPublisher = eventPublisher;
    }

    @Async
    public void run(UUID analysisId, String baseUrl) {
        log.info("Pipeline starting for analysis {} url={}", analysisId, baseUrl);
        UUID projectId = analysisRepository.findById(analysisId)
                .map(Analysis::getProjectId)
                .orElse(null);
        try {
            // Load per-project crawl settings
            CrawlOptions crawlOptions = (projectId == null) ? CrawlOptions.defaults()
                    : projectSettingsRepository.findByProjectId(projectId)
                            .map(s -> new CrawlOptions(
                                    s.getMaxPages(),
                                    s.getAuthUrl(),
                                    s.getAuthUsername(),
                                    s.getAuthPassword(),
                                    parsePatterns(s.getExcludedPatterns())))
                            .orElse(CrawlOptions.defaults());

            // Step 1: Crawl
            progress.emit(analysisId, new ProgressEvent("CRAWLING", "Crawling " + baseUrl + "…", 5));
            List<PageInfo> crawled = crawlerService.crawl(baseUrl, crawlOptions);
            log.info("Crawled {} pages for analysis {}", crawled.size(), analysisId);

            if (crawled.isEmpty()) {
                log.warn("No pages discovered for {} — URL may be unreachable or JS-gated", baseUrl);
                progress.emit(analysisId, new ProgressEvent("FAILED", "No pages could be reached at " + baseUrl, 0));
                updateStatus(analysisId, "FAILED");
                progress.complete(analysisId);
                if (projectId != null) eventPublisher.publishEvent(new AnalysisNotificationEvent(analysisId, projectId, baseUrl, "FAILED"));
                return;
            }

            progress.emit(analysisId, new ProgressEvent("CRAWLING",
                    "Found " + crawled.size() + " page" + (crawled.size() == 1 ? "" : "s"), 20));

            // Steps 2–4: one shared browser for all test executions
            try (Playwright pw = Playwright.create()) {
                Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setChromiumSandbox(false)
                        .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run")));
                try {
                    int total = crawled.size();
                    for (int i = 0; i < total; i++) {
                        PageInfo info = crawled.get(i);

                        // Step 2: Persist discovered page
                        Page page = new Page();
                        page.setAnalysisId(analysisId);
                        page.setUrl(info.getUrl());
                        page.setTitle(info.getTitle());
                        page.setPageType(classifyPage(info.getUrl()));
                        pageRepository.save(page);

                        // Step 3: Discover UI elements
                        int discoverProgress = 20 + (int) ((i + 1) * 15.0 / total);
                        progress.emit(analysisId, new ProgressEvent("DISCOVERING",
                                "Scanning elements on " + info.getUrl(), discoverProgress));
                        try {
                            uiElementDiscoveryService.discover(page.getId(), info.getHtmlContent());
                        } catch (Exception e) {
                            log.debug("UI element discovery skipped for {}: {}", info.getUrl(), e.getMessage());
                        }

                        // Step 4: Generate tests
                        int genProgress = 35 + (int) ((i + 1) * 15.0 / total);
                        progress.emit(analysisId, new ProgressEvent("GENERATING",
                                "Generating tests for " + info.getUrl(), genProgress));
                        List<GeneratedTest> tests;
                        try {
                            tests = testGenerationService.generateForPage(page.getId(), info.getUrl());
                        } catch (Exception e) {
                            log.warn("Test generation failed for {}: {}", info.getUrl(), e.getMessage());
                            continue;
                        }

                        // Step 5: Execute tests
                        int execProgress = 50 + (int) ((i + 1) * 40.0 / total);
                        progress.emit(analysisId, new ProgressEvent("EXECUTING",
                                "Running " + tests.size() + " test" + (tests.size() == 1 ? "" : "s") +
                                " on " + info.getUrl(), execProgress));
                        for (GeneratedTest test : tests) {
                            try {
                                TestExecution execution = executionService.execute(test, analysisId, browser);
                                bugDetectionService.detectFromExecution(execution, analysisId);
                            } catch (Exception e) {
                                log.warn("Execution failed for test '{}': {}", test.getName(), e.getMessage());
                            }
                        }
                    }
                } finally {
                    browser.close();
                }
            }

            // Step 6: Generate report
            progress.emit(analysisId, new ProgressEvent("REPORTING", "Generating quality report…", 92));
            try {
                reportService.generate(analysisId, ReportFormat.JSON);
            } catch (Exception e) {
                log.warn("Report generation failed for analysis {}: {}", analysisId, e.getMessage());
            }

            progress.emit(analysisId, new ProgressEvent("COMPLETED", "Analysis complete", 100));
            log.info("Pipeline COMPLETED for analysis {}", analysisId);
            updateStatus(analysisId, "COMPLETED");
            if (projectId != null) eventPublisher.publishEvent(new AnalysisNotificationEvent(analysisId, projectId, baseUrl, "COMPLETED"));

        } catch (Exception e) {
            log.error("Pipeline FAILED for analysis {} url={}", analysisId, baseUrl, e);
            progress.emit(analysisId, new ProgressEvent("FAILED", "Pipeline error: " + e.getMessage(), 0));
            updateStatus(analysisId, "FAILED");
            if (projectId != null) eventPublisher.publishEvent(new AnalysisNotificationEvent(analysisId, projectId, baseUrl, "FAILED"));
        } finally {
            progress.complete(analysisId);
        }
    }

    private void updateStatus(UUID analysisId, String status) {
        analysisRepository.findById(analysisId).ifPresent(a -> {
            a.setStatus(status);
            a.setCompletedAt(OffsetDateTime.now());
            analysisRepository.save(a);
        });
    }

    private List<String> parsePatterns(String patterns) {
        if (patterns == null || patterns.isBlank()) return List.of();
        return Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
    }

    private String classifyPage(String url) {
        String lower = url.toLowerCase();
        if (lower.contains("login") || lower.contains("signin")) return "LOGIN";
        if (lower.contains("register") || lower.contains("signup")) return "REGISTER";
        if (lower.contains("checkout") || lower.contains("payment")) return "CHECKOUT";
        if (lower.contains("product") || lower.contains("item")) return "PRODUCT";
        if (lower.contains("profile") || lower.contains("account")) return "PROFILE";
        if (lower.contains("cart") || lower.contains("basket")) return "CART";
        return "GENERAL";
    }
}