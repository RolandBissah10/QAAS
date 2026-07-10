package com.qaas.apitest.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_endpoints")
public class ApiEndpoint {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "analysis_id", nullable = false, columnDefinition = "uuid")
    private UUID analysisId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "observed_status")
    private Integer observedStatus;

    @Column(name = "requires_auth")
    private boolean requiresAuth;

    @Column(name = "discovered_at", nullable = false, updatable = false)
    private Instant discoveredAt;

    protected ApiEndpoint() {}

    public ApiEndpoint(UUID analysisId, String method, String url, int observedStatus) {
        this.id = UUID.randomUUID();
        this.analysisId = analysisId;
        this.method = method.toUpperCase();
        this.url = url;
        this.observedStatus = observedStatus;
        this.requiresAuth = (observedStatus == 401 || observedStatus == 403);
        this.discoveredAt = Instant.now();
    }

    public UUID getId()             { return id; }
    public UUID getAnalysisId()     { return analysisId; }
    public String getUrl()          { return url; }
    public String getMethod()       { return method; }
    public Integer getObservedStatus() { return observedStatus; }
    public boolean isRequiresAuth() { return requiresAuth; }
    public Instant getDiscoveredAt() { return discoveredAt; }

    public void markRequiresAuth()  { this.requiresAuth = true; }
}