package com.qaas.evidence.controller;

import com.qaas.evidence.entity.Screenshot;
import com.qaas.evidence.repository.ScreenshotRepository;
import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/screenshots")
public class ScreenshotController {

    private final ScreenshotRepository repository;
    private final ProjectService projectService;

    public ScreenshotController(ScreenshotRepository repository, ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    public record ScreenshotDto(UUID id, UUID analysisId, String path, OffsetDateTime capturedAt) {
        static ScreenshotDto from(Screenshot s) {
            return new ScreenshotDto(s.getId(), s.getAnalysisId(), s.getPath(), s.getCapturedAt());
        }
    }

    @GetMapping("/analysis/{analysisId}")
    List<ScreenshotDto> byAnalysis(@PathVariable UUID analysisId, Authentication auth) {
        projectService.verifyAnalysisAccess(analysisId, auth.getName());
        return repository.findByAnalysisId(analysisId)
                .stream()
                .map(ScreenshotDto::from)
                .toList();
    }

    @GetMapping("/{id}/image")
    ResponseEntity<byte[]> image(@PathVariable UUID id, Authentication auth) {
        Screenshot screenshot = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Screenshot not found"));
        projectService.verifyAnalysisAccess(screenshot.getAnalysisId(), auth.getName());

        if (screenshot.getPath() == null) {
            throw new NotFoundException("Screenshot has no file path");
        }

        Path path = Path.of(screenshot.getPath());
        if (!Files.exists(path)) {
            throw new NotFoundException("Screenshot file not found on disk");
        }

        try {
            byte[] content = Files.readAllBytes(path);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/png")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read screenshot file", e);
        }
    }
}