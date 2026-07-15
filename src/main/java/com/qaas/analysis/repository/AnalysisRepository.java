package com.qaas.analysis.repository;

import com.qaas.analysis.entity.Analysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    List<Analysis> findByProjectId(UUID projectId);
    Page<Analysis> findByProjectId(UUID projectId, Pageable pageable);
    List<Analysis> findByProjectIdIn(Collection<UUID> projectIds);
    boolean existsByProjectIdAndUrlAndStatus(UUID projectId, String url, String status);
    List<Analysis> findByStatus(String status);

    @Modifying
    @Transactional
    @Query("UPDATE Analysis a SET a.status = 'CANCELLED', a.completedAt = :now WHERE a.id = :id AND a.status = 'RUNNING'")
    int cancelIfRunning(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE Analysis a SET a.status = 'FAILED', a.completedAt = :now WHERE a.status = 'RUNNING'")
    int failAllRunning(@Param("now") OffsetDateTime now);
}
