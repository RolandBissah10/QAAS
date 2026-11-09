package com.qaas.environment.entity;

import com.qaas.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_environments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "name"}))
public class TestEnvironment {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @Column(name = "auth_username", length = 255)
    private String authUsername;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "auth_password", length = 2048)
    private String authPassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TestEnvironment() {
    }

    public TestEnvironment(UUID projectId, String name, String baseUrl,
                           String authUsername, String authPassword) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.name = name;
        this.baseUrl = baseUrl;
        this.authUsername = authUsername;
        this.authPassword = authPassword;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getAuthUsername() { return authUsername; }
    public String getAuthPassword() { return authPassword; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String baseUrl, String authUsername, String authPassword,
                       boolean clearCredentials) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.authUsername = authUsername;
        if (clearCredentials) {
            this.authUsername = null;
            this.authPassword = null;
        } else if (authPassword != null && !authPassword.isBlank()) {
            this.authPassword = authPassword;
        }
        this.updatedAt = Instant.now();
    }

    public boolean hasCredentials() {
        return authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank();
    }
}
