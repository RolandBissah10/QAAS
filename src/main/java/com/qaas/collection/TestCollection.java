package com.qaas.collection;

import com.qaas.common.BaseEntity;
import com.qaas.project.Project;
import com.qaas.test.ApiTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "collections")
public class TestCollection extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToMany
    @JoinTable(
            name = "collection_tests",
            joinColumns = @JoinColumn(name = "collection_id"),
            inverseJoinColumns = @JoinColumn(name = "test_id")
    )
    private Set<ApiTest> tests = new LinkedHashSet<>();

    protected TestCollection() {
    }

    public TestCollection(Project project, String name, String description, Set<ApiTest> tests) {
        update(project, name, description, tests);
    }

    public void update(Project project, String name, String description, Set<ApiTest> tests) {
        this.project = project;
        this.name = name;
        this.description = description;
        this.tests = tests;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<ApiTest> getTests() {
        return tests;
    }
}
