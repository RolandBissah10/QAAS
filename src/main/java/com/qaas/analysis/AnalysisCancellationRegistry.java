package com.qaas.analysis;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe registry of cancellation flags for running analyses.
 * The pipeline registers an ID when it starts and polls isCancelled() at each
 * checkpoint. The stop endpoint flips the flag; the pipeline exits at the next
 * safe boundary and deregisters the ID.
 */
@Component
public class AnalysisCancellationRegistry {

    private final Map<UUID, AtomicBoolean> flags = new ConcurrentHashMap<>();

    public void register(UUID analysisId) {
        flags.put(analysisId, new AtomicBoolean(false));
    }

    public void cancel(UUID analysisId) {
        AtomicBoolean flag = flags.get(analysisId);
        if (flag != null) flag.set(true);
    }

    public boolean isCancelled(UUID analysisId) {
        AtomicBoolean flag = flags.get(analysisId);
        return flag != null && flag.get();
    }

    public void deregister(UUID analysisId) {
        flags.remove(analysisId);
    }
}