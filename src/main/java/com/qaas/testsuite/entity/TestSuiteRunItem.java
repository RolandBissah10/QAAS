package com.qaas.testsuite.entity;

import com.qaas.execution.TestExecution;
import com.qaas.generator.entity.GeneratedTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_suite_run_items")
public class TestSuiteRunItem {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private TestSuiteRun run;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private GeneratedTest test;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private TestExecution execution;

    @Column(name = "test_name", nullable = false, length = 255)
    private String testName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestSuiteItemStatus status;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TestSuiteRunItem() {
    }

    public TestSuiteRunItem(TestSuiteRun run, GeneratedTest test) {
        this.id = UUID.randomUUID();
        this.run = run;
        this.test = test;
        this.testName = test.getName() == null ? "Unnamed test" : test.getName();
        this.status = TestSuiteItemStatus.QUEUED;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TestSuiteRun getRun() { return run; }
    public GeneratedTest getTest() { return test; }
    public TestExecution getExecution() { return execution; }
    public String getTestName() { return testName; }
    public TestSuiteItemStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }

    public void start() {
        this.status = TestSuiteItemStatus.RUNNING;
        this.errorMessage = null;
    }

    public void complete(TestExecution execution) {
        this.execution = execution;
        this.status = switch (execution.getStatus()) {
            case PASSED -> TestSuiteItemStatus.PASSED;
            case ERROR -> TestSuiteItemStatus.ERROR;
            default -> TestSuiteItemStatus.FAILED;
        };
        this.errorMessage = execution.getErrorMessage();
    }

    public void fail(String errorMessage) {
        this.status = TestSuiteItemStatus.ERROR;
        this.errorMessage = errorMessage;
    }
}
