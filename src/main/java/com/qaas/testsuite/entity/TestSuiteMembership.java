package com.qaas.testsuite.entity;

import com.qaas.generator.entity.GeneratedTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_suite_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"suite_id", "test_id"}))
public class TestSuiteMembership {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id", nullable = false)
    private TestSuite suite;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private GeneratedTest test;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TestSuiteMembership() {
    }

    public TestSuiteMembership(TestSuite suite, GeneratedTest test) {
        this.id = UUID.randomUUID();
        this.suite = suite;
        this.test = test;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TestSuite getSuite() { return suite; }
    public GeneratedTest getTest() { return test; }
    public Instant getCreatedAt() { return createdAt; }
}
