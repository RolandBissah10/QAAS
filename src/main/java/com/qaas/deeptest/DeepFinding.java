package com.qaas.deeptest;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deep_findings")
public class DeepFinding {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false)
    private UUID analysisId;

    /** SECURITY, ACCESSIBILITY, PERFORMANCE, CONSOLE_ERROR, BROKEN_LINK */
    @Column(nullable = false)
    private String category;

    /** HIGH, MEDIUM, LOW */
    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(length = 2048)
    private String pageUrl;

    @Column(nullable = false)
    private Instant detectedAt;

    protected DeepFinding() {}

    public DeepFinding(UUID analysisId, String category, String severity,
                       String title, String description, String pageUrl) {
        this.id = UUID.randomUUID();
        this.analysisId = analysisId;
        this.category = category;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.pageUrl = pageUrl;
        this.detectedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAnalysisId() { return analysisId; }
    public String getCategory() { return category; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getPageUrl() { return pageUrl; }
    public Instant getDetectedAt() { return detectedAt; }
}