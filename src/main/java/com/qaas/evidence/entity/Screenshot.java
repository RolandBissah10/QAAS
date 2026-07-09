package com.qaas.evidence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "screenshots")
public class Screenshot {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false)
    private UUID analysisId;

    @Column
    private String path;

    @Column
    private OffsetDateTime capturedAt;

    public Screenshot() { this.id = UUID.randomUUID(); }
    public UUID getId() { return id; }
    public UUID getAnalysisId() { return analysisId; }
    public void setAnalysisId(UUID analysisId) { this.analysisId = analysisId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(OffsetDateTime capturedAt) { this.capturedAt = capturedAt; }
}
