package com.qaas.bug;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BugRepository extends JpaRepository<Bug, UUID> {
    List<Bug> findByAnalysisId(UUID analysisId);
    Page<Bug> findByAnalysisId(UUID analysisId, Pageable pageable);
    long countBySeverity(Severity severity);
    long countByStatus(BugStatus status);
    long countByAnalysisIdIn(Collection<UUID> analysisIds);
    long countBySeverityAndAnalysisIdIn(Severity severity, Collection<UUID> analysisIds);
}
