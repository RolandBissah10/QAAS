package com.qaas.execution;

import com.qaas.generator.entity.GeneratedTest;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "executions")
public class TestExecution {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "test_id")
    private GeneratedTest test;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    protected TestExecution() {
    }

    public TestExecution(GeneratedTest test) {
        this.id = UUID.randomUUID();
        this.test = test;
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public GeneratedTest getTest() { return test; }
    public ExecutionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorMessage() { return errorMessage; }

    public void pass() {
        this.status = ExecutionStatus.PASSED;
        this.completedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = ExecutionStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
}
