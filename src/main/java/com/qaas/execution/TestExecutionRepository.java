package com.qaas.execution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TestExecutionRepository extends JpaRepository<TestExecution, UUID> {
    @Query("SELECT e FROM TestExecution e WHERE e.test.pageId IN " +
           "(SELECT p.id FROM com.qaas.page.entity.Page p WHERE p.analysisId = :analysisId)")
    List<TestExecution> findByAnalysisId(UUID analysisId);

    @Query(value = "SELECT e FROM TestExecution e WHERE e.test.pageId IN " +
                   "(SELECT p.id FROM com.qaas.page.entity.Page p WHERE p.analysisId = :analysisId)",
           countQuery = "SELECT COUNT(e) FROM TestExecution e WHERE e.test.pageId IN " +
                        "(SELECT p.id FROM com.qaas.page.entity.Page p WHERE p.analysisId = :analysisId)")
    Page<TestExecution> findByAnalysisId(@Param("analysisId") UUID analysisId, Pageable pageable);

    long countByStatus(ExecutionStatus status);
}
