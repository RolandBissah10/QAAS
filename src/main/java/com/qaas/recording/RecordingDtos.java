package com.qaas.recording;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RecordingDtos {

    public record RecordingResponse(
        UUID id,
        UUID projectId,
        String name,
        String targetUrl,
        String status,
        int entryCount,
        int apiEndpointCount,
        OffsetDateTime capturedAt,
        OffsetDateTime createdAt,
        String errorMessage
    ) {}

    public record EntryResponse(
        UUID id,
        String method,
        String url,
        int statusCode,
        String requestHeaders,
        String requestBody,
        String responseHeaders,
        String responseBody,
        double timeTaken,
        OffsetDateTime startedAt,
        int entryIndex
    ) {}

    public record RecordingStats(
        long totalEntries,
        long errorCount,
        double avgTimeTakenMs
    ) {}
}