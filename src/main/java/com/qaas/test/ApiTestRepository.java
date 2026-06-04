package com.qaas.test;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApiTestRepository extends JpaRepository<ApiTest, UUID> {
}
