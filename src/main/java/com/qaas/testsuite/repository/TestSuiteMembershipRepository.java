package com.qaas.testsuite.repository;

import com.qaas.testsuite.entity.TestSuiteMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface TestSuiteMembershipRepository extends JpaRepository<TestSuiteMembership, UUID> {
    @Query("SELECT m FROM TestSuiteMembership m JOIN FETCH m.test WHERE m.suite.id = :suiteId ORDER BY m.createdAt")
    List<TestSuiteMembership> findWithTestsBySuiteId(@Param("suiteId") UUID suiteId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TestSuiteMembership m WHERE m.suite.id = :suiteId")
    void deleteBySuiteId(@Param("suiteId") UUID suiteId);
}
