package com.qaas.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TestExecutionRepository extends JpaRepository<TestExecution, UUID> {
    long countByStatus(ExecutionStatus status);

    List<TestExecution> findTop30ByExecutedAtAfterOrderByExecutedAtAsc(Instant after);
}
