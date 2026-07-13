package com.qaas.generator.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "generated_tests")
public class GeneratedTest {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid")
    private UUID pageId;

    // Populated for API tests (no page association)
    @Column(name = "analysis_id", columnDefinition = "uuid")
    private UUID analysisId;

    // Links to the ApiEndpoint this test exercises (null for UI tests)
    @Column(name = "api_endpoint_id", columnDefinition = "uuid")
    private UUID apiEndpointId;

    @Column
    private String name;

    @Column
    private String type;

    @Column
    private String status;

    @Column
    private String targetUrl;

    @Column(name = "script_json", columnDefinition = "TEXT")
    private String scriptJson;

    public GeneratedTest() { this.id = UUID.randomUUID(); }

    public UUID getId()              { return id; }
    public UUID getPageId()          { return pageId; }
    public void setPageId(UUID v)    { this.pageId = v; }
    public UUID getAnalysisId()      { return analysisId; }
    public void setAnalysisId(UUID v){ this.analysisId = v; }
    public UUID getApiEndpointId()         { return apiEndpointId; }
    public void setApiEndpointId(UUID v)   { this.apiEndpointId = v; }
    public String getName()          { return name; }
    public void setName(String v)    { this.name = v; }
    public String getType()          { return type; }
    public void setType(String v)    { this.type = v; }
    public String getStatus()        { return status; }
    public void setStatus(String v)  { this.status = v; }
    public String getTargetUrl()       { return targetUrl; }
    public void setTargetUrl(String v) { this.targetUrl = v; }
    public String getScriptJson()      { return scriptJson; }
    public void setScriptJson(String v){ this.scriptJson = v; }
}