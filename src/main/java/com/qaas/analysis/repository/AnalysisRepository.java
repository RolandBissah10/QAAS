package com.qaas.analysis.repository;

import com.qaas.analysis.entity.Analysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    List<Analysis> findByProjectId(UUID projectId);
    Page<Analysis> findByProjectId(UUID projectId, Pageable pageable);
    boolean existsByProjectIdAndUrlAndStatus(UUID projectId, String url, String status);
}
