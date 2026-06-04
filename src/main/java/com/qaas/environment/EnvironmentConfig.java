package com.qaas.environment;

import com.qaas.common.BaseEntity;
import com.qaas.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "environments")
public class EnvironmentConfig extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    protected EnvironmentConfig() {
    }

    public EnvironmentConfig(Project project, String name, String baseUrl) {
        this.project = project;
        this.name = name;
        this.baseUrl = baseUrl;
    }

    public void update(String name, String baseUrl) {
        this.name = name;
        this.baseUrl = baseUrl;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
