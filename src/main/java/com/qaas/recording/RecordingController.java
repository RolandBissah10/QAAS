package com.qaas.recording;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recordings")
public class RecordingController {

    private final RecordingService service;

    public RecordingController(RecordingService service) {
        this.service = service;
    }

    @GetMapping
    List<RecordingDtos.RecordingResponse> list(@RequestParam UUID projectId, Authentication auth) {
        return service.listByProject(auth.getName(), projectId);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RecordingDtos.RecordingResponse upload(
            @RequestParam UUID projectId,
            @RequestParam(defaultValue = "") String name,
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        return service.upload(auth.getName(), projectId, name, file);
    }

    @GetMapping("/{id}")
    RecordingDtos.RecordingResponse get(@PathVariable UUID id, Authentication auth) {
        return service.get(auth.getName(), id);
    }

    @GetMapping("/{id}/entries")
    Page<RecordingDtos.EntryResponse> entries(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            Authentication auth) {
        return service.getEntries(auth.getName(), id, PageRequest.of(page, Math.min(size, 500)));
    }

    @GetMapping("/{id}/stats")
    RecordingDtos.RecordingStats stats(@PathVariable UUID id, Authentication auth) {
        return service.getStats(auth.getName(), id);
    }

    @PostMapping("/capture")
    RecordingDtos.RecordingResponse capture(@RequestParam UUID projectId, Authentication auth) {
        return service.startCapture(auth.getName(), projectId);
    }

    @PostMapping("/{id}/stop")
    void stop(@PathVariable UUID id, Authentication auth) {
        service.stopCapture(auth.getName(), id);
    }

    @DeleteMapping("/{id}")
    void delete(@PathVariable UUID id, Authentication auth) {
        service.delete(auth.getName(), id);
    }
}