package com.qaas.execution;

import com.qaas.common.BaseEntity;
import com.qaas.test.ApiTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "executions")
public class TestExecution extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "test_id")
    private ApiTest test;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected TestExecution() {
    }

    public TestExecution(ApiTest test, ExecutionStatus status) {
        this.test = test;
        this.status = status;
        this.executedAt = Instant.now();
    }

    public ApiTest getTest() {
        return test;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
