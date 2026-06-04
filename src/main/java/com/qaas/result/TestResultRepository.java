package com.qaas.result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {
    Optional<TestResult> findByExecutionId(UUID executionId);

    long countByPassed(boolean passed);

    @Query("select coalesce(avg(r.responseTime), 0) from TestResult r")
    double averageResponseTime();
}
