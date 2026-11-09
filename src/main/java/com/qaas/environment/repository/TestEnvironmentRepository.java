package com.qaas.environment.repository;

import com.qaas.environment.entity.TestEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestEnvironmentRepository extends JpaRepository<TestEnvironment, UUID> {
    List<TestEnvironment> findByProjectIdOrderByNameAsc(UUID projectId);
    Optional<TestEnvironment> findByIdAndProjectId(UUID id, UUID projectId);
    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);
}
