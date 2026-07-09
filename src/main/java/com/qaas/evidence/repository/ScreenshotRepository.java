package com.qaas.evidence.repository;

import com.qaas.evidence.entity.Screenshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScreenshotRepository extends JpaRepository<Screenshot, UUID> {
    List<Screenshot> findByAnalysisId(UUID analysisId);
}
