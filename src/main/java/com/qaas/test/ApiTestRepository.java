package com.qaas.test;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApiTestRepository extends JpaRepository<ApiTest, UUID> {
	java.util.List<ApiTest> findByProjectId(UUID projectId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("delete from ApiTest t where t.project.id = :projectId")
	void deleteByProjectId(java.util.UUID projectId);
}
