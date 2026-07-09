package com.qaas.project.settings;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_settings")
public class ProjectSettings {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false, unique = true)
    private UUID projectId;

    @Column(nullable = false)
    private int maxPages = 20;

    @Column(length = 2048)
    private String authUrl;

    @Column(length = 255)
    private String authUsername;

    @Column(length = 1024)
    private String authPassword;

    /** Comma-separated URL substrings to skip during crawl (e.g. "/logout,/admin"). */
    @Column(length = 4096)
    private String excludedPatterns;

    private OffsetDateTime updatedAt;

    public ProjectSettings() {
        this.id = UUID.randomUUID();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
    public String getAuthUrl() { return authUrl; }
    public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }
    public String getAuthUsername() { return authUsername; }
    public void setAuthUsername(String authUsername) { this.authUsername = authUsername; }
    public String getAuthPassword() { return authPassword; }
    public void setAuthPassword(String authPassword) { this.authPassword = authPassword; }
    public String getExcludedPatterns() { return excludedPatterns; }
    public void setExcludedPatterns(String excludedPatterns) { this.excludedPatterns = excludedPatterns; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}