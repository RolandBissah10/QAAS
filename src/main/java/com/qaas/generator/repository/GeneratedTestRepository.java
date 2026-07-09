package com.qaas.generator.repository;

import com.qaas.generator.entity.GeneratedTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GeneratedTestRepository extends JpaRepository<GeneratedTest, UUID> {
    List<GeneratedTest> findByPageId(UUID pageId);

    @Query(value = "SELECT t FROM GeneratedTest t WHERE t.pageId IN " +
                   "(SELECT p.id FROM com.qaas.page.entity.Page p WHERE p.analysisId = :analysisId)",
           countQuery = "SELECT COUNT(t) FROM GeneratedTest t WHERE t.pageId IN " +
                        "(SELECT p.id FROM com.qaas.page.entity.Page p WHERE p.analysisId = :analysisId)")
    Page<GeneratedTest> findByAnalysisId(@Param("analysisId") UUID analysisId, Pageable pageable);
}
