package com.qaas.recording;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recording_entries")
public class RecordingEntry {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false)
    private UUID recordingId;

    @Column
    private String method;

    @Column(length = 2048)
    private String url;

    @Column
    private int statusCode;

    @Column(columnDefinition = "text")
    private String requestHeaders;

    @Column(columnDefinition = "text")
    private String requestBody;

    @Column(columnDefinition = "text")
    private String responseHeaders;

    @Column(columnDefinition = "text")
    private String responseBody;

    @Column
    private double timeTaken;

    @Column
    private OffsetDateTime startedAt;

    @Column
    private int entryIndex;

    public RecordingEntry() {
        this.id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public UUID getRecordingId() { return recordingId; }
    public void setRecordingId(UUID recordingId) { this.recordingId = recordingId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public String getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(String requestHeaders) { this.requestHeaders = requestHeaders; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(String responseHeaders) { this.responseHeaders = responseHeaders; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public double getTimeTaken() { return timeTaken; }
    public void setTimeTaken(double timeTaken) { this.timeTaken = timeTaken; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public int getEntryIndex() { return entryIndex; }
    public void setEntryIndex(int entryIndex) { this.entryIndex = entryIndex; }
}