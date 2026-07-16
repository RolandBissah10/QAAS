package com.qaas.page.repository;

import com.qaas.page.entity.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PageRepository extends JpaRepository<Page, UUID> {
    List<Page> findByAnalysisId(UUID analysisId);
    org.springframework.data.domain.Page<Page> findByAnalysisId(UUID analysisId, Pageable pageable);
    long countByAnalysisIdIn(Collection<UUID> analysisIds);

    @org.springframework.data.jpa.repository.Query(
            "SELECT p.analysisId, COUNT(p) FROM Page p WHERE p.analysisId IN :ids GROUP BY p.analysisId")
    java.util.List<Object[]> countGroupByAnalysisId(
            @org.springframework.data.repository.query.Param("ids") Collection<UUID> ids);
}
