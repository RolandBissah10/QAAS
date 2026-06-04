package com.qaas.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TestExecutionRepository extends JpaRepository<TestExecution, UUID> {
    long countByStatus(ExecutionStatus status);

    List<TestExecution> findTop30ByExecutedAtAfterOrderByExecutedAtAsc(Instant after);
    java.util.List<TestExecution> findByTestId(UUID testId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from TestExecution e where e.test.id in (select t.id from ApiTest t where t.project.id = :projectId)")
    void deleteByProjectId(java.util.UUID projectId);
}
