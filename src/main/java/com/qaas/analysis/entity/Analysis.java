package com.qaas.analysis.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "analyses")
public class Analysis {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid")
    private UUID projectId;

    @Column(columnDefinition = "uuid")
    private UUID triggeredByUserId;

    @Column(nullable = false)
    private String url;

    @Column
    private String status;

    @Column
    private OffsetDateTime startedAt;

    @Column
    private OffsetDateTime completedAt;

    @Column(columnDefinition = "boolean default false")
    private boolean deepTest = false;

    public Analysis() { this.id = UUID.randomUUID(); }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getTriggeredByUserId() { return triggeredByUserId; }
    public void setTriggeredByUserId(UUID triggeredByUserId) { this.triggeredByUserId = triggeredByUserId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public boolean isDeepTest() { return deepTest; }
    public void setDeepTest(boolean deepTest) { this.deepTest = deepTest; }
}
