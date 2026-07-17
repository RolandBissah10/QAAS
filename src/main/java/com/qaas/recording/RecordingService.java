package com.qaas.recording;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.HarContentPolicy;
import com.microsoft.playwright.options.HarMode;
import com.microsoft.playwright.options.WaitUntilState;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.page.repository.PageRepository;
import com.qaas.project.repository.ProjectMemberRepository;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RecordingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);
    private static final int MAX_BODY_CHARS = 100_000;
    private static final String[] STATIC_EXTS = {
        ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
        ".woff", ".woff2", ".ttf", ".eot", ".map", ".webp", ".avif", ".mp4", ".mp3"
    };

    private final RecordingRepository recordingRepo;
    private final RecordingEntryRepository entryRepo;
    private final ProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final AnalysisRepository analysisRepo;
    private final PageRepository pageRepo;
    private final ObjectMapper objectMapper;
    private final RecordingCancellationRegistry captureRegistry;

    // Field injection required: constructor injection would be circular.
    // @Lazy lets Spring resolve the proxy after the bean graph is built.
    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Lazy @Autowired private RecordingService self;

    public RecordingService(RecordingRepository recordingRepo,
                            RecordingEntryRepository entryRepo,
                            ProjectRepository projectRepo,
                            ProjectMemberRepository memberRepo,
                            UserRepository userRepo,
                            AnalysisRepository analysisRepo,
                            PageRepository pageRepo,
                            ObjectMapper objectMapper,
                            RecordingCancellationRegistry captureRegistry) {
        this.recordingRepo = recordingRepo;
        this.entryRepo = entryRepo;
        this.projectRepo = projectRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.analysisRepo = analysisRepo;
        this.pageRepo = pageRepo;
        this.objectMapper = objectMapper;
        this.captureRegistry = captureRegistry;
    }

    // ── Upload (HAR file from user) ───────────────────────────────────────────

    @Transactional
    public RecordingDtos.RecordingResponse upload(String userEmail, UUID projectId,
                                                   String name, MultipartFile file) {
        UUID userId = resolveUserId(userEmail);
        checkProjectAccess(userId, projectId);

        Recording recording = new Recording();
        recording.setProjectId(projectId);
        recording.setName(name.isBlank() ? (file.getOriginalFilename() != null ? file.getOriginalFilename() : "Recording") : name);
        recording.setStatus("PROCESSING");
        recordingRepo.save(recording);

        try {
            parseHarAndSave(recording, objectMapper.readTree(file.getInputStream()));
        } catch (Exception e) {
            recording.setStatus("ERROR");
            recording.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Failed to parse HAR file");
            recordingRepo.save(recording);
        }

        return toDto(recording);
    }

    // ── Auto-capture (HAR from analysis pipeline) ─────────────────────────────

    @Transactional
    public void processHarFromAnalysis(UUID projectId, UUID analysisId, String baseUrl, Path harFile) {
        Recording recording = new Recording();
        recording.setProjectId(projectId);
        recording.setName("Analysis: " + baseUrl);
        recording.setTargetUrl(extractHost(baseUrl));
        recording.setStatus("PROCESSING");
        recordingRepo.save(recording);

        try {
            JsonNode root = objectMapper.readTree(harFile.toFile());
            parseHarAndSave(recording, root);
            log.info("Auto-recording created from analysis {} ({} entries)", analysisId, recording.getEntryCount());
        } catch (Exception e) {
            recording.setStatus("ERROR");
            recording.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Failed to process analysis HAR");
            recordingRepo.save(recording);
            log.warn("Failed to create recording from analysis {}: {}", analysisId, e.getMessage());
        }
    }

    // ── Standalone capture (user-triggered Playwright session) ────────────────

    @Transactional
    public void stopCapture(String userEmail, UUID recordingId) {
        Recording r = findAndAuthorize(userEmail, recordingId);
        if (!"CAPTURING".equals(r.getStatus()) && !"PROCESSING".equals(r.getStatus())) return;
        captureRegistry.cancel(recordingId);
        r.setStatus("CANCELLED");
        r.setErrorMessage("Stopped by user");
        recordingRepo.save(r);
    }

    @Transactional
    public RecordingDtos.RecordingResponse startCapture(String userEmail, UUID projectId) {
        UUID userId = resolveUserId(userEmail);
        checkProjectAccess(userId, projectId);

        String baseUrl = projectRepo.findById(projectId)
                .map(p -> p.getBaseUrl())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project has no URL configured");
        }

        Recording recording = new Recording();
        recording.setProjectId(projectId);
        recording.setName("Live Capture: " + extractHost(baseUrl));
        recording.setTargetUrl(extractHost(baseUrl));
        recording.setStatus("CAPTURING");
        recordingRepo.save(recording);

        self.runCapture(projectId, recording.getId());
        return toDto(recording);
    }

    @Async
    public void runCapture(UUID projectId, UUID recordingId) {
        Recording recording = recordingRepo.findById(recordingId).orElse(null);
        if (recording == null) return;

        captureRegistry.register(recordingId);
        Path harFile = null;
        try {
            String baseUrl = projectRepo.findById(projectId).map(p -> p.getBaseUrl()).orElse(null);
            if (baseUrl == null) throw new IllegalStateException("Project has no URL");

            Set<String> urlsToVisit = new LinkedHashSet<>();
            urlsToVisit.add(baseUrl);
            analysisRepo.findTopByProjectIdAndStatusOrderByStartedAtDesc(projectId, "COMPLETED")
                    .ifPresent(a -> pageRepo.findByAnalysisId(a.getId()).stream()
                            .map(com.qaas.page.entity.Page::getUrl)
                            .distinct().limit(50).forEach(urlsToVisit::add));

            harFile = Files.createTempFile("capture-" + recordingId, ".har");
            final Path finalHarFile = harFile;

            try (Playwright pw = Playwright.create()) {
                Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true).setChromiumSandbox(false)
                        .setArgs(List.of("--disable-dev-shm-usage", "--no-first-run")));

                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setRecordHarPath(finalHarFile)
                        .setRecordHarContent(HarContentPolicy.EMBED)
                        .setRecordHarMode(HarMode.FULL));

                com.microsoft.playwright.Page page = context.newPage();

                for (String url : urlsToVisit) {
                    if (captureRegistry.isCancelled(recordingId)) break;
                    try {
                        page.navigate(url, new com.microsoft.playwright.Page.NavigateOptions()
                                .setTimeout(20_000).setWaitUntil(WaitUntilState.NETWORKIDLE));
                        log.debug("Capture navigated to {}", url);
                    } catch (Exception e) {
                        log.debug("Capture: skipping unreachable {}: {}", url, e.getMessage());
                    }
                }

                context.close(); // flushes HAR to disk
                browser.close();
            }

            // Skip saving if the user cancelled while we were running
            if (captureRegistry.isCancelled(recordingId)) {
                log.info("Capture cancelled for recording {}", recordingId);
                return;
            }
            // Also check DB in case stopCapture fired before register() was called
            Recording fresh = recordingRepo.findById(recordingId).orElse(null);
            if (fresh == null || "CANCELLED".equals(fresh.getStatus())) return;

            JsonNode root = objectMapper.readTree(harFile.toFile());
            parseHarAndSave(recording, root);
            log.info("Standalone capture complete for project {} ({} entries)", projectId, recording.getEntryCount());

        } catch (Exception e) {
            if (!captureRegistry.isCancelled(recordingId)) {
                log.error("Capture task failed for recording {}: {}", recordingId, e.getMessage(), e);
                recording.setStatus("ERROR");
                recording.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Capture failed");
                recordingRepo.save(recording);
            }
        } finally {
            captureRegistry.deregister(recordingId);
            if (harFile != null) { try { Files.deleteIfExists(harFile); } catch (Exception ignored) {} }
        }
    }

    // ── Read operations ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RecordingDtos.RecordingResponse> listByProject(String userEmail, UUID projectId) {
        UUID userId = resolveUserId(userEmail);
        checkProjectAccess(userId, projectId);
        return recordingRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RecordingDtos.RecordingResponse get(String userEmail, UUID recordingId) {
        return toDto(findAndAuthorize(userEmail, recordingId));
    }

    @Transactional(readOnly = true)
    public Page<RecordingDtos.EntryResponse> getEntries(String userEmail, UUID recordingId, Pageable pageable) {
        findAndAuthorize(userEmail, recordingId);
        return entryRepo.findByRecordingIdOrderByEntryIndex(recordingId, pageable).map(this::toEntryDto);
    }

    @Transactional(readOnly = true)
    public RecordingDtos.RecordingStats getStats(String userEmail, UUID recordingId) {
        Recording r = findAndAuthorize(userEmail, recordingId);
        long errors = entryRepo.countErrorsByRecordingId(recordingId);
        Double avg = entryRepo.avgTimeTakenByRecordingId(recordingId);
        return new RecordingDtos.RecordingStats(r.getEntryCount(), errors, avg != null ? avg : 0.0);
    }

    @Transactional
    public void delete(String userEmail, UUID recordingId) {
        Recording r = findAndAuthorize(userEmail, recordingId);
        entryRepo.deleteByRecordingId(recordingId);
        recordingRepo.delete(r);
    }

    // ── HAR parsing ───────────────────────────────────────────────────────────

    private void parseHarAndSave(Recording recording, JsonNode root) {
        JsonNode entries = root.path("log").path("entries");
        if (!entries.isArray()) throw new IllegalArgumentException("Invalid HAR: missing log.entries array");

        List<RecordingEntry> entryList = new ArrayList<>();
        int apiCount = 0;
        OffsetDateTime capturedAt = null;
        String targetUrl = null;

        for (int i = 0; i < entries.size(); i++) {
            JsonNode node = entries.get(i);
            RecordingEntry entry = parseEntry(node, recording.getId(), i);
            entryList.add(entry);

            if (capturedAt == null && entry.getStartedAt() != null) {
                capturedAt = entry.getStartedAt();
                if (recording.getTargetUrl() == null) targetUrl = extractHost(entry.getUrl());
            }
            if (isApiRequest(node)) apiCount++;
        }

        entryRepo.saveAll(entryList);
        recording.setEntryCount(entryList.size());
        recording.setApiEndpointCount(apiCount);
        recording.setCapturedAt(capturedAt);
        if (recording.getTargetUrl() == null) recording.setTargetUrl(targetUrl);
        recording.setStatus("READY");
        recordingRepo.save(recording);
    }

    private RecordingEntry parseEntry(JsonNode node, UUID recordingId, int index) {
        RecordingEntry entry = new RecordingEntry();
        entry.setRecordingId(recordingId);
        entry.setEntryIndex(index);

        JsonNode req = node.path("request");
        JsonNode res = node.path("response");

        entry.setMethod(req.path("method").asText(null));
        String url = req.path("url").asText(null);
        entry.setUrl(url != null && url.length() > 2048 ? url.substring(0, 2048) : url);
        entry.setStatusCode(res.path("status").asInt(0));
        entry.setTimeTaken(node.path("time").asDouble(0));

        String startedStr = node.path("startedDateTime").asText(null);
        if (startedStr != null && !startedStr.isBlank()) {
            try { entry.setStartedAt(OffsetDateTime.parse(startedStr)); } catch (Exception ignored) {}
        }

        entry.setRequestHeaders(nodeToJson(req.path("headers")));
        entry.setResponseHeaders(nodeToJson(res.path("headers")));

        JsonNode postData = req.path("postData");
        if (!postData.isMissingNode() && !postData.isNull()) {
            entry.setRequestBody(truncate(postData.path("text").asText(null)));
        }

        if (!isStaticAsset(url)) {
            String body = res.path("content").path("text").asText(null);
            if (body != null) entry.setResponseBody(truncate(body));
        }

        return entry;
    }

    private boolean isApiRequest(JsonNode node) {
        String url = node.path("request").path("url").asText("");
        String method = node.path("request").path("method").asText("GET").toUpperCase();
        if (isStaticAsset(url)) return false;
        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) return true;

        JsonNode headers = node.path("response").path("headers");
        if (headers.isArray()) {
            for (JsonNode h : headers) {
                if ("content-type".equalsIgnoreCase(h.path("name").asText("")) &&
                    h.path("value").asText("").contains("application/json")) return true;
            }
        }

        try {
            String path = new URI(url).getPath();
            if (path == null) return false;
            return path.contains("/api/") || path.contains("/v1/") || path.contains("/v2/")
                || path.contains("/v3/") || path.contains("/rest/") || path.contains("/graphql");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStaticAsset(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase().split("[?#]")[0];
        for (String ext : STATIC_EXTS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private String extractHost(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            int port = uri.getPort();
            return (host != null && port > 0) ? host + ":" + port : host;
        } catch (Exception e) {
            return url;
        }
    }

    private String nodeToJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try { return objectMapper.writeValueAsString(node); } catch (Exception e) { return null; }
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_BODY_CHARS) return text;
        return text.substring(0, MAX_BODY_CHARS) + "\n…[truncated]";
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private UUID resolveUserId(String email) {
        return userRepo.findByEmail(email)
                .map(u -> u.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void checkProjectAccess(UUID userId, UUID projectId) {
        boolean ok = projectRepo.findById(projectId)
                .map(p -> p.getOwnerId().equals(userId) ||
                          memberRepo.findByProjectIdAndUserId(projectId, userId).isPresent())
                .orElse(false);
        if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to this project");
    }

    private Recording findAndAuthorize(String userEmail, UUID recordingId) {
        Recording r = recordingRepo.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        checkProjectAccess(resolveUserId(userEmail), r.getProjectId());
        return r;
    }

    private RecordingDtos.RecordingResponse toDto(Recording r) {
        return new RecordingDtos.RecordingResponse(
            r.getId(), r.getProjectId(), r.getName(), r.getTargetUrl(),
            r.getStatus(), r.getEntryCount(), r.getApiEndpointCount(),
            r.getCapturedAt(), r.getCreatedAt(), r.getErrorMessage()
        );
    }

    private RecordingDtos.EntryResponse toEntryDto(RecordingEntry e) {
        return new RecordingDtos.EntryResponse(
            e.getId(), e.getMethod(), e.getUrl(), e.getStatusCode(),
            e.getRequestHeaders(), e.getRequestBody(),
            e.getResponseHeaders(), e.getResponseBody(),
            e.getTimeTaken(), e.getStartedAt(), e.getEntryIndex()
        );
    }
}