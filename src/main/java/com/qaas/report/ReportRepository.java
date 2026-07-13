package com.qaas.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    List<Report> findByAnalysisId(UUID analysisId);
    Optional<Report> findByAnalysisIdAndFormat(UUID analysisId, ReportFormat format);
    List<Report> findTop20ByFormatOrderByGeneratedAtDesc(ReportFormat format);
}
