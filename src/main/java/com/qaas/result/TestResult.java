package com.qaas.result;

import com.qaas.common.BaseEntity;
import com.qaas.execution.TestExecution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "results")
public class TestResult extends BaseEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "execution_id")
    private TestExecution execution;

    @Column(name = "response_time", nullable = false)
    private long responseTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private Map<String, Object> responseBody = new LinkedHashMap<>();

    @Column(nullable = false)
    private boolean passed;

    protected TestResult() {
    }

    public TestResult(TestExecution execution, long responseTime, Map<String, Object> responseBody, boolean passed) {
        this.execution = execution;
        this.responseTime = responseTime;
        this.responseBody = responseBody;
        this.passed = passed;
    }

    public TestExecution getExecution() {
        return execution;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public Map<String, Object> getResponseBody() {
        return responseBody;
    }

    public boolean isPassed() {
        return passed;
    }
}
