package com.qaas.deeptest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeepFindingRepository extends JpaRepository<DeepFinding, UUID> {
    List<DeepFinding> findByAnalysisIdOrderByDetectedAtDesc(UUID analysisId);
}