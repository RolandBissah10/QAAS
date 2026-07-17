package com.qaas.recording;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RecordingCancellationRegistry {

    private final Map<UUID, AtomicBoolean> flags = new ConcurrentHashMap<>();

    public void register(UUID recordingId) {
        flags.put(recordingId, new AtomicBoolean(false));
    }

    public void cancel(UUID recordingId) {
        AtomicBoolean flag = flags.get(recordingId);
        if (flag != null) flag.set(true);
    }

    public boolean isCancelled(UUID recordingId) {
        AtomicBoolean flag = flags.get(recordingId);
        return flag != null && flag.get();
    }

    public void deregister(UUID recordingId) {
        flags.remove(recordingId);
    }
}