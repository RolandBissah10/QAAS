package com.qaas.environment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EnvironmentRepository extends JpaRepository<EnvironmentConfig, UUID> {
	java.util.List<EnvironmentConfig> findByProjectId(UUID projectId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("delete from EnvironmentConfig e where e.project.id = :projectId")
	void deleteByProjectId(java.util.UUID projectId);
}
