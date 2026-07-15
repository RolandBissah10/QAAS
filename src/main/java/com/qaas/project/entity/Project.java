package com.qaas.project.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(columnDefinition = "uuid")
    private UUID ownerId;

    @Column(length = 2048)
    private String baseUrl;

    @Column
    private OffsetDateTime createdAt;

    public Project() { this.id = UUID.randomUUID(); this.createdAt = OffsetDateTime.now(); }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
