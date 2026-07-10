package com.qaas.apitest.repository;

import com.qaas.apitest.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, UUID> {
    List<ApiEndpoint> findByAnalysisId(UUID analysisId);
}