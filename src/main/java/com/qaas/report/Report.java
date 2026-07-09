package com.qaas.report;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class Report {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false)
    private UUID analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportFormat format;

    @Column
    private String filePath;

    @Column(nullable = false)
    private Instant generatedAt;

    @Column
    private Integer qualityScore;

    @Column
    private Integer totalTests;

    @Column
    private Integer passedTests;

    @Column
    private Integer failedTests;

    @Column
    private Integer bugCount;

    @Column
    private Integer pagesDiscovered;

    protected Report() {
    }

    public Report(UUID analysisId, ReportFormat format) {
        this.id = UUID.randomUUID();
        this.analysisId = analysisId;
        this.format = format;
        this.generatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAnalysisId() { return analysisId; }
    public ReportFormat getFormat() { return format; }
    public String getFilePath() { return filePath; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Integer getQualityScore() { return qualityScore; }
    public Integer getTotalTests() { return totalTests; }
    public Integer getPassedTests() { return passedTests; }
    public Integer getFailedTests() { return failedTests; }
    public Integer getBugCount() { return bugCount; }
    public Integer getPagesDiscovered() { return pagesDiscovered; }

    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
    public void setTotalTests(Integer totalTests) { this.totalTests = totalTests; }
    public void setPassedTests(Integer passedTests) { this.passedTests = passedTests; }
    public void setFailedTests(Integer failedTests) { this.failedTests = failedTests; }
    public void setBugCount(Integer bugCount) { this.bugCount = bugCount; }
    public void setPagesDiscovered(Integer pagesDiscovered) { this.pagesDiscovered = pagesDiscovered; }
}
