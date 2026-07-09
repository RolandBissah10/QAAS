package com.qaas.page.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pages")
public class Page {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid")
    private UUID analysisId;

    @Column(nullable = false)
    private String url;

    @Column
    private String title;

    @Column
    private String pageType;

    @Column
    private OffsetDateTime discoveredAt;

    public Page() { this.id = UUID.randomUUID(); this.discoveredAt = OffsetDateTime.now(); }
    public UUID getId() { return id; }
    public UUID getAnalysisId() { return analysisId; }
    public void setAnalysisId(UUID analysisId) { this.analysisId = analysisId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }
    public OffsetDateTime getDiscoveredAt() { return discoveredAt; }
}
