package com.qaas.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestCollectionRepository extends JpaRepository<TestCollection, UUID> {
	java.util.List<TestCollection> findByProjectId(UUID projectId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("delete from TestCollection c where c.project.id = :projectId")
	void deleteByProjectId(java.util.UUID projectId);
}
