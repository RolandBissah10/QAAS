package com.qaas.report;

import com.qaas.exception.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;
    private final ReportRepository repository;

    public ReportController(ReportService service, ReportRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/analysis/{analysisId}")
    @ResponseStatus(HttpStatus.CREATED)
    ReportDtos.ReportResponse generate(
            @PathVariable UUID analysisId,
            @RequestBody ReportDtos.GenerateReportRequest request) {
        return ReportDtos.ReportResponse.from(service.generate(analysisId, request.format()));
    }

    @GetMapping("/analysis/{analysisId}")
    List<ReportDtos.ReportResponse> byAnalysis(@PathVariable UUID analysisId) {
        return service.getByAnalysis(analysisId);
    }

    @GetMapping("/{id}/download")
    ResponseEntity<byte[]> download(@PathVariable UUID id) {
        Report report = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Report not found"));

        if (report.getFilePath() == null) {
            throw new NotFoundException("Report file has not been generated yet");
        }

        Path path = Path.of(report.getFilePath());
        if (!Files.exists(path)) {
            throw new NotFoundException("Report file not found on disk");
        }

        try {
            byte[] content = Files.readAllBytes(path);
            String contentType = switch (report.getFormat()) {
                case JSON -> "application/json";
                case HTML -> "text/html";
                case PDF  -> "application/pdf";
            };
            String ext = report.getFormat().name().toLowerCase();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"report-" + id + "." + ext + "\"")
                    .body(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read report file", e);
        }
    }
}