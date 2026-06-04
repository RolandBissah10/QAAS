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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from TestResult r where r.execution.id in (select e.id from TestExecution e join e.test t where t.project.id = :projectId)")
    void deleteByProjectId(java.util.UUID projectId);
}
