package com.qaas.testsuite.repository;

import com.qaas.testsuite.entity.TestSuiteRun;
import com.qaas.testsuite.entity.TestSuiteRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TestSuiteRunRepository extends JpaRepository<TestSuiteRun, UUID> {
    Optional<TestSuiteRun> findByIdAndSuite_Id(UUID id, UUID suiteId);
    Page<TestSuiteRun> findBySuite_Id(UUID suiteId, Pageable pageable);
    boolean existsBySuite_IdAndStatusIn(UUID suiteId, Collection<TestSuiteRunStatus> statuses);

    @Modifying
    @Transactional
    @Query("DELETE FROM TestSuiteRun r WHERE r.suite.id = :suiteId")
    void deleteBySuiteId(@Param("suiteId") UUID suiteId);
}
