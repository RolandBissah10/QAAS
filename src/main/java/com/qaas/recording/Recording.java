package com.qaas.recording;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recordings")
public class Recording {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2048)
    private String targetUrl;

    @Column(nullable = false)
    private String status;

    @Column
    private int entryCount;

    @Column
    private int apiEndpointCount;

    @Column
    private OffsetDateTime capturedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(columnDefinition = "text")
    private String errorMessage;

    public Recording() {
        this.id = UUID.randomUUID();
        this.createdAt = OffsetDateTime.now();
        this.status = "PROCESSING";
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getEntryCount() { return entryCount; }
    public void setEntryCount(int entryCount) { this.entryCount = entryCount; }
    public int getApiEndpointCount() { return apiEndpointCount; }
    public void setApiEndpointCount(int apiEndpointCount) { this.apiEndpointCount = apiEndpointCount; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(OffsetDateTime capturedAt) { this.capturedAt = capturedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}