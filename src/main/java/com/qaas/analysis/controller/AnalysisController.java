package com.qaas.analysis.controller;

import com.qaas.analysis.AnalysisCancellationRegistry;
import com.qaas.analysis.ProgressEmitterRegistry;
import com.qaas.analysis.entity.Analysis;
import com.qaas.analysis.repository.AnalysisRepository;
import com.qaas.analysis.service.AnalysisPipelineService;
import com.qaas.common.PagedResponse;
import com.qaas.exception.ConflictException;
import com.qaas.exception.NotFoundException;
import com.qaas.project.repository.ProjectRepository;
import com.qaas.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisRepository analysisRepository;
    private final ProjectRepository projectRepository;
    private final AnalysisPipelineService pipelineService;
    private final ProgressEmitterRegistry emitterRegistry;
    private final UserRepository userRepository;
    private final AnalysisCancellationRegistry cancellationRegistry;

    public AnalysisController(AnalysisRepository analysisRepository,
                               ProjectRepository projectRepository,
                               AnalysisPipelineService pipelineService,
                               ProgressEmitterRegistry emitterRegistry,
                               UserRepository userRepository,
                               AnalysisCancellationRegistry cancellationRegistry) {
        this.analysisRepository = analysisRepository;
        this.projectRepository = projectRepository;
        this.pipelineService = pipelineService;
        this.emitterRegistry = emitterRegistry;
        this.userRepository = userRepository;
        this.cancellationRegistry = cancellationRegistry;
    }

    public record StartRequest(@NotBlank String url) {}

    public record AnalysisResponse(UUID id, UUID projectId, String url, String status,
                                   OffsetDateTime startedAt, OffsetDateTime completedAt) {
        static AnalysisResponse from(Analysis a) {
            return new AnalysisResponse(a.getId(), a.getProjectId(), a.getUrl(),
                    a.getStatus(), a.getStartedAt(), a.getCompletedAt());
        }
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisResponse start(@RequestParam UUID projectId, @RequestBody StartRequest req, Authentication auth) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        if (analysisRepository.existsByProjectIdAndUrlAndStatus(projectId, req.url(), "RUNNING")) {
            throw new ConflictException("An analysis is already running for this URL in this project");
        }

        Analysis analysis = new Analysis();
        analysis.setProjectId(projectId);
        analysis.setUrl(req.url());
        analysis.setStatus("RUNNING");
        analysis.setStartedAt(OffsetDateTime.now());
        if (auth != null) {
            userRepository.findByEmail(auth.getName())
                    .ifPresent(u -> analysis.setTriggeredByUserId(u.getId()));
        }
        analysisRepository.save(analysis);

        // Register BEFORE dispatching so any stop() call that arrives before the
        // background thread starts still finds a flag to flip.
        cancellationRegistry.register(analysis.getId());
        pipelineService.run(analysis.getId(), req.url());

        return AnalysisResponse.from(analysis);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResponse> get(@PathVariable UUID id) {
        return analysisRepository.findById(id)
                .map(AnalysisResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<String> status(@PathVariable UUID id) {
        return analysisRepository.findById(id)
                .map(a -> ResponseEntity.ok(a.getStatus()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@PathVariable UUID id) {
        return emitterRegistry.subscribe(id);
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable UUID id) {
        if (!analysisRepository.existsById(id)) return ResponseEntity.notFound().build();
        // Signal the live pipeline thread (no-op if thread is already gone)
        cancellationRegistry.cancel(id);
        // Atomic UPDATE — only fires if status is currently RUNNING; returns 0 for
        // zombie analyses whose status is already terminal.
        int updated = analysisRepository.cancelIfRunning(id, OffsetDateTime.now());
        return updated > 0
                ? ResponseEntity.accepted().build()
                : ResponseEntity.noContent().build();
    }

    @GetMapping("/project/{projectId}")
    public PagedResponse<AnalysisResponse> byProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var paged = analysisRepository.findByProjectId(projectId,
                PageRequest.of(page, size, Sort.by("startedAt").descending()));
        return PagedResponse.fromMapped(paged, paged.getContent().stream()
                .map(AnalysisResponse::from).toList());
    }
}