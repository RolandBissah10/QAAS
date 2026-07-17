package com.qaas.recording;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface RecordingEntryRepository extends JpaRepository<RecordingEntry, UUID> {

    Page<RecordingEntry> findByRecordingIdOrderByEntryIndex(UUID recordingId, Pageable pageable);

    @Query("SELECT COUNT(e) FROM RecordingEntry e WHERE e.recordingId = :id AND e.statusCode >= 400")
    long countErrorsByRecordingId(@Param("id") UUID id);

    @Query("SELECT AVG(e.timeTaken) FROM RecordingEntry e WHERE e.recordingId = :id")
    Double avgTimeTakenByRecordingId(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM RecordingEntry e WHERE e.recordingId = :id")
    void deleteByRecordingId(@Param("id") UUID id);
}