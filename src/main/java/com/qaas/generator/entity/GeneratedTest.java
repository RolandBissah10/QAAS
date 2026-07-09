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

    @Column
    private String name;

    @Column
    private String type;

    @Column
    private String status;

    @Column
    private String targetUrl;

    public GeneratedTest() { this.id = UUID.randomUUID(); }
    public UUID getId() { return id; }
    public UUID getPageId() { return pageId; }
    public void setPageId(UUID pageId) { this.pageId = pageId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
}
