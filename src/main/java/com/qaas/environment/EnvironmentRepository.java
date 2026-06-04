package com.qaas.environment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EnvironmentRepository extends JpaRepository<EnvironmentConfig, UUID> {
}
