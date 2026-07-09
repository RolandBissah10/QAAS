package com.qaas.bug;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bugs")
public class Bug {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false)
    private UUID executionId;

    @Column(columnDefinition = "uuid", nullable = false)
    private UUID analysisId;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BugStatus status;

    @Column(nullable = false)
    private Instant detectedAt;

    protected Bug() {
    }

    public Bug(UUID executionId, UUID analysisId, String title, String description, Severity severity) {
        this.id = UUID.randomUUID();
        this.executionId = executionId;
        this.analysisId = analysisId;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.status = BugStatus.OPEN;
        this.detectedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getExecutionId() { return executionId; }
    public UUID getAnalysisId() { return analysisId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Severity getSeverity() { return severity; }
    public BugStatus getStatus() { return status; }
    public Instant getDetectedAt() { return detectedAt; }
    public void setStatus(BugStatus status) { this.status = status; }
}
