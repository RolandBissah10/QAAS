package com.qaas.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.HarContentPolicy;
import com.microsoft.playwright.options.HarMode;
import com.qaas.recording.RecordingService;
import com.qaas.analysis.AnalysisCancellationRegistry;
import com.qaas.analysis.ProgressEmitterRegistry;
import com.qaas.analysis.dto.ProgressEvent;
import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.apitest.entity.ApiEndpoint;
import com.qaas.apitest.repository.ApiEndpointRepository;
import com.qaas.apitest.service.ApiTestGenerationService;
import com.qaas.bug.BugDetectionService;
import com.qaas.crawler.dto.ApiEndpointInfo;
import com.qaas.crawler.dto.CrawlOptions;
import com.qaas.crawler.dto.CrawlResult;
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
import com.qaas.deeptest.DeepTestService;
import com.qaas.notification.AnalysisNotificationEvent;
import com.qaas.report.ReportFormat;
import com.qaas.report.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiTestGenerationService apiTestGenerationService;
    private final ObjectMapper objectMapper;
    private final AnalysisCancellationRegistry cancellationRegistry;
    private final DeepTestService deepTestService;
    private final RecordingService recordingService;

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
                                   ApplicationEventPublisher eventPublisher,
                                   ApiEndpointRepository apiEndpointRepository,
                                   ApiTestGenerationService apiTestGenerationService,
                                   ObjectMapper objectMapper,
                                   AnalysisCancellationRegistry cancellationRegistry,
                                   DeepTestService deepTestService,
                                   RecordingService recordingService) {
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
        this.apiEndpointRepository = apiEndpointRepository;
        this.apiTestGenerationService = apiTestGenerationService;
        this.objectMapper = objectMapper;
        this.cancellationRegistry = cancellationRegistry;
        this.deepTestService = deepTestService;
        this.recordingService = recordingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resetOrphanedAnalyses() {
        int fixed = analysisRepository.failAllRunning(OffsetDateTime.now());
        if (fixed > 0) log.warn("Reset {} orphaned RUNNING analysis/analyses to FAILED on startup", fixed);
    }

    @Async
    public void run(UUID analysisId, String baseUrl) {
        run(analysisId, baseUrl, false);
    }

    @Async
    public void run(UUID analysisId, String baseUrl, boolean deepTest) {
        log.info("Pipeline starting for analysis {} url={}", analysisId, baseUrl);

        Analysis meta = analysisRepository.findById(analysisId).orElse(null);
        UUID projectId         = meta != null ? meta.getProjectId()         : null;
        UUID triggeredByUserId = meta != null ? meta.getTriggeredByUserId() : null;

        try {
            CrawlOptions crawlOptions = (projectId == null) ? CrawlOptions.defaults()
                    : projectSettingsRepository.findByProjectId(projectId)
                            .map(s -> new CrawlOptions(
                                    s.getMaxPages(),
                                    s.getAuthUrl(),
                                    s.getAuthUsername(),
                                    s.getAuthPassword(),
                                    parsePatterns(s.getExcludedPatterns())))
                            .orElse(CrawlOptions.defaults());

            // Step 1: Crawl — pass cancel checker so BFS exits early if stop is requested
            progress.emit(analysisId, new ProgressEvent("CRAWLING", "Crawling " + baseUrl + "…", 5));
            CrawlResult crawlResult = crawlerService.crawl(
                    baseUrl, crawlOptions, () -> cancellationRegistry.isCancelled(analysisId));

            // Check immediately after the crawl returns
            if (cancellationRegistry.isCancelled(analysisId)) {
                handleCancellation(analysisId, baseUrl, projectId, triggeredByUserId);
                return;
            }

            List<PageInfo> crawled = crawlResult.pages();
            log.info("Crawled {} pages for analysis {}", crawled.size(), analysisId);

            if (crawled.isEmpty()) {
                log.warn("No pages discovered for {} — URL may be unreachable or JS-gated", baseUrl);
                progress.emit(analysisId, new ProgressEvent("FAILED", "No pages could be reached at " + baseUrl, 0));
                updateStatus(analysisId, "FAILED");
                progress.complete(analysisId);
                if (projectId != null) eventPublisher.publishEvent(
                        new AnalysisNotificationEvent(analysisId, projectId, triggeredByUserId, baseUrl, "FAILED"));
                return;
            }

            // Persist API endpoints observed during the crawl
            List<ApiEndpoint> persistedEndpoints = new ArrayList<>();
            for (ApiEndpointInfo info : crawlResult.apiEndpoints()) {
                try {
                    persistedEndpoints.add(apiEndpointRepository.save(
                            new ApiEndpoint(analysisId, info.method(), info.url(), info.status())));
                } catch (Exception e) {
                    log.warn("Could not persist API endpoint {}: {}", info.url(), e.getMessage());
                }
            }
            if (!persistedEndpoints.isEmpty()) {
                log.info("Persisted {} API endpoints for analysis {}", persistedEndpoints.size(), analysisId);
            }

            progress.emit(analysisId, new ProgressEvent("CRAWLING",
                    "Found " + crawled.size() + " page" + (crawled.size() == 1 ? "" : "s")
                    + (persistedEndpoints.isEmpty() ? "" : " and " + persistedEndpoints.size() + " API endpoint(s)"),
                    20));

            // Steps 2–5: one shared browser context for all test executions
            Path harFile = Files.createTempFile("analysis-" + analysisId, ".har");
            try (Playwright pw = Playwright.create()) {
                Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setChromiumSandbox(false)
                        .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run")));

                Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                        .setRecordHarPath(harFile)
                        .setRecordHarContent(HarContentPolicy.EMBED)
                        .setRecordHarMode(HarMode.FULL);
                if (crawlResult.storageStateJson() != null) {
                    ctxOpts = ctxOpts.setStorageState(crawlResult.storageStateJson());
                }
                BrowserContext sharedContext = browser.newContext(ctxOpts);
                try {
                    int total = crawled.size();
                    for (int i = 0; i < total; i++) {
                        // Check before each page — exit cleanly without processing further pages
                        if (cancellationRegistry.isCancelled(analysisId)) {
                            log.info("Pipeline cancelled at page {}/{} for analysis {}", i, total, analysisId);
                            break;
                        }

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
                            tests = testGenerationService.generateForPage(page.getId(), info.getUrl(), info.getHtmlContent());
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
                            if (cancellationRegistry.isCancelled(analysisId)) break;
                            try {
                                TestExecution execution = executionService.execute(test, analysisId, sharedContext);
                                bugDetectionService.detectFromExecution(execution, analysisId);
                            } catch (Exception e) {
                                log.warn("Execution failed for test '{}': {}", test.getName(), e.getMessage());
                            }
                        }
                    }
                } finally {
                    sharedContext.close(); // flushes HAR to disk
                    browser.close();
                }

                // Auto-create a recording from the captured HAR (non-fatal)
                if (projectId != null && !cancellationRegistry.isCancelled(analysisId)) {
                    try {
                        recordingService.processHarFromAnalysis(projectId, analysisId, baseUrl, harFile);
                    } catch (Exception e) {
                        log.warn("Auto-recording failed for analysis {}: {}", analysisId, e.getMessage());
                    }
                }
            } finally {
                Files.deleteIfExists(harFile);
            }

            // Check again before API tests
            if (cancellationRegistry.isCancelled(analysisId)) {
                handleCancellation(analysisId, baseUrl, projectId, triggeredByUserId);
                return;
            }

            // Step 6: Generate and execute API tests
            if (!persistedEndpoints.isEmpty()) {
                progress.emit(analysisId, new ProgressEvent("EXECUTING",
                        "Running API tests for " + persistedEndpoints.size() + " endpoint(s)…", 88));
                String authToken = extractAuthToken(crawlResult.storageStateJson());
                for (ApiEndpoint endpoint : persistedEndpoints) {
                    if (cancellationRegistry.isCancelled(analysisId)) break;
                    List<GeneratedTest> apiTests;
                    try {
                        apiTests = apiTestGenerationService.generateForEndpoint(endpoint);
                    } catch (Exception e) {
                        log.warn("API test generation failed for {}: {}", endpoint.getUrl(), e.getMessage());
                        continue;
                    }
                    for (GeneratedTest apiTest : apiTests) {
                        if (cancellationRegistry.isCancelled(analysisId)) break;
                        try {
                            TestExecution execution = executionService.executeApiTest(apiTest, analysisId, authToken);
                            bugDetectionService.detectFromExecution(execution, analysisId);
                        } catch (Exception e) {
                            log.warn("API test execution failed for {}: {}", apiTest.getTargetUrl(), e.getMessage());
                        }
                    }
                }
            }

            // Final cancellation check before optional deep tests + report
            if (cancellationRegistry.isCancelled(analysisId)) {
                handleCancellation(analysisId, baseUrl, projectId, triggeredByUserId);
                return;
            }

            // Step 7 (optional): Deep test — security, accessibility, performance, broken links
            if (deepTest) {
                progress.emit(analysisId, new ProgressEvent("DEEP_TESTING",
                        "Running deep checks: security headers, accessibility, performance, broken links…", 90));
                try {
                    deepTestService.run(analysisId, crawled);
                } catch (Exception e) {
                    log.warn("Deep test step failed for analysis {}: {}", analysisId, e.getMessage());
                }
            }

            // Step 8: Generate report
            progress.emit(analysisId, new ProgressEvent("REPORTING", "Generating quality report…", 92));
            try {
                reportService.generate(analysisId, ReportFormat.JSON);
            } catch (Exception e) {
                log.warn("Report generation failed for analysis {}: {}", analysisId, e.getMessage());
            }

            progress.emit(analysisId, new ProgressEvent("COMPLETED", "Analysis complete", 100));
            log.info("Pipeline COMPLETED for analysis {}", analysisId);
            updateStatus(analysisId, "COMPLETED");
            if (projectId != null) eventPublisher.publishEvent(
                    new AnalysisNotificationEvent(analysisId, projectId, triggeredByUserId, baseUrl, "COMPLETED"));

        } catch (Exception e) {
            log.error("Pipeline FAILED for analysis {} url={}", analysisId, baseUrl, e);
            progress.emit(analysisId, new ProgressEvent("FAILED", "Pipeline error: " + e.getMessage(), 0));
            updateStatus(analysisId, "FAILED");
            if (projectId != null) eventPublisher.publishEvent(
                    new AnalysisNotificationEvent(analysisId, projectId, triggeredByUserId, baseUrl, "FAILED"));
        } finally {
            cancellationRegistry.deregister(analysisId);
            progress.complete(analysisId);
        }
    }

    private void handleCancellation(UUID analysisId, String baseUrl, UUID projectId, UUID triggeredByUserId) {
        log.info("Pipeline CANCELLED for analysis {} url={}", analysisId, baseUrl);
        progress.emit(analysisId, new ProgressEvent("CANCELLED", "Analysis stopped by user", 0));
        updateStatus(analysisId, "CANCELLED");
        if (projectId != null) eventPublisher.publishEvent(
                new AnalysisNotificationEvent(analysisId, projectId, triggeredByUserId, baseUrl, "CANCELLED"));
    }

    private String extractAuthToken(String storageStateJson) {
        if (storageStateJson == null || storageStateJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(storageStateJson);
            for (JsonNode origin : root.path("origins")) {
                for (JsonNode entry : origin.path("localStorage")) {
                    if ("qaas.auth".equals(entry.path("name").asText())) {
                        JsonNode auth = objectMapper.readTree(entry.path("value").asText());
                        String token = auth.path("accessToken").asText(null);
                        if (token != null && !token.isBlank()) return token;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract auth token from storage state: {}", e.getMessage());
        }
        return null;
    }

    private void updateStatus(UUID analysisId, String status) {
        analysisRepository.findById(analysisId).ifPresent(a -> {
            // Never overwrite CANCELLED with COMPLETED/FAILED — stop endpoint may have
            // already written CANCELLED (e.g. for zombie analyses), and we must not undo it.
            if ("CANCELLED".equals(a.getStatus()) && !"CANCELLED".equals(status)) return;
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
        // Extract path only (ignore query params/fragments which cause false matches)
        String path;
        try {
            path = new java.net.URI(url).getPath();
            if (path == null) path = "";
        } catch (Exception e) {
            path = url;
        }
        String lower = path.toLowerCase();
        if (lower.contains("login") || lower.contains("signin")) return "LOGIN";
        if (lower.contains("register") || lower.contains("signup")) return "REGISTER";
        if (lower.contains("checkout") || lower.contains("payment")) return "CHECKOUT";
        // Require "/product" or "/products" as a path segment — avoid matching query params or unrelated words
        if (lower.matches(".*/products?(/.*)?") ) return "PRODUCT";
        if (lower.contains("profile") || lower.contains("account")) return "PROFILE";
        if (lower.contains("cart") || lower.contains("basket")) return "CART";
        return "GENERAL";
    }
}