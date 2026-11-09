package com.qaas.testsuite.entity;

import com.qaas.environment.EnvironmentDtos.RunEnvironment;
import com.qaas.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "test_suite_runs")
public class TestSuiteRun {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id", nullable = false)
    private TestSuite suite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestSuiteRunStatus status;

    @Column(name = "total_tests", nullable = false)
    private int totalTests;

    @Column(name = "passed_tests", nullable = false)
    private int passedTests;

    @Column(name = "failed_tests", nullable = false)
    private int failedTests;

    @Column(name = "error_tests", nullable = false)
    private int errorTests;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "environment_id", columnDefinition = "uuid")
    private UUID environmentId;

    @Column(name = "environment_name", length = 100)
    private String environmentName;

    @Column(name = "environment_base_url", length = 2048)
    private String environmentBaseUrl;

    @Column(name = "environment_auth_username", length = 255)
    private String environmentAuthUsername;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "environment_auth_password", length = 2048)
    private String environmentAuthPassword;

    protected TestSuiteRun() {
    }

    public TestSuiteRun(TestSuite suite, int totalTests) {
        this(suite, totalTests, null);
    }

    public TestSuiteRun(TestSuite suite, int totalTests, RunEnvironment environment) {
        this.id = UUID.randomUUID();
        this.suite = suite;
        this.status = TestSuiteRunStatus.QUEUED;
        this.totalTests = totalTests;
        this.createdAt = Instant.now();
        if (environment != null) {
            this.environmentId = environment.id();
            this.environmentName = environment.name();
            this.environmentBaseUrl = environment.baseUrl();
            this.environmentAuthUsername = environment.authUsername();
            this.environmentAuthPassword = environment.authPassword();
        }
    }

    public UUID getId() { return id; }
    public TestSuite getSuite() { return suite; }
    public TestSuiteRunStatus getStatus() { return status; }
    public int getTotalTests() { return totalTests; }
    public int getPassedTests() { return passedTests; }
    public int getFailedTests() { return failedTests; }
    public int getErrorTests() { return errorTests; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getEnvironmentId() { return environmentId; }
    public String getEnvironmentName() { return environmentName; }
    public String getEnvironmentBaseUrl() { return environmentBaseUrl; }
    public String getEnvironmentAuthUsername() { return environmentAuthUsername; }
    public String getEnvironmentAuthPassword() { return environmentAuthPassword; }

    public void start() {
        this.status = TestSuiteRunStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void complete(int passed, int failed, int errors) {
        this.passedTests = passed;
        this.failedTests = failed;
        this.errorTests = errors;
        this.status = failed == 0 && errors == 0
                ? TestSuiteRunStatus.PASSED : TestSuiteRunStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public void failBeforeStart() {
        this.status = TestSuiteRunStatus.FAILED;
        this.completedAt = Instant.now();
    }
}
