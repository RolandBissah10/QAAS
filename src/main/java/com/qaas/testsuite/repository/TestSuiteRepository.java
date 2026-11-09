package com.qaas.testsuite.repository;

import com.qaas.testsuite.entity.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestSuiteRepository extends JpaRepository<TestSuite, UUID> {
    List<TestSuite> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<TestSuite> findByIdAndProjectId(UUID id, UUID projectId);
}
