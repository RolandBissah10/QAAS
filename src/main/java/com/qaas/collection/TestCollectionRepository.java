package com.qaas.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestCollectionRepository extends JpaRepository<TestCollection, UUID> {
}
