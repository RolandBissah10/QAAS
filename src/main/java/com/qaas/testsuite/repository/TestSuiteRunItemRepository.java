package com.qaas.testsuite.repository;

import com.qaas.testsuite.entity.TestSuiteItemStatus;
import com.qaas.testsuite.entity.TestSuiteRunItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface TestSuiteRunItemRepository extends JpaRepository<TestSuiteRunItem, UUID> {
    List<TestSuiteRunItem> findByRun_IdOrderByCreatedAt(UUID runId);

    @Query("SELECT i.id FROM TestSuiteRunItem i WHERE i.run.id = :runId ORDER BY i.createdAt")
    List<UUID> findIdsByRunId(@Param("runId") UUID runId);

    long countByRun_IdAndStatus(UUID runId, TestSuiteItemStatus status);

    @Modifying
    @Transactional
    @Query("DELETE FROM TestSuiteRunItem i WHERE i.run.suite.id = :suiteId")
    void deleteBySuiteId(@Param("suiteId") UUID suiteId);
}
