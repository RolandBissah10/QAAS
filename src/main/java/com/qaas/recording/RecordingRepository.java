package com.qaas.recording;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RecordingRepository extends JpaRepository<Recording, UUID> {
    List<Recording> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}