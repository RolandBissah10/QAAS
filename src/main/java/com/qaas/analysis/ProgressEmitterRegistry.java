package com.qaas.analysis;

import com.qaas.analysis.dto.ProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ProgressEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProgressEmitterRegistry.class);
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L; // 10 minutes

    private final Map<UUID, List<SseEmitter>> registry = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID analysisId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        registry.computeIfAbsent(analysisId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(analysisId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    public void emit(UUID analysisId, ProgressEvent event) {
        List<SseEmitter> emitters = registry.getOrDefault(analysisId, List.of());
        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }

        if (!dead.isEmpty()) {
            emitters.removeAll(dead);
            log.debug("Removed {} dead emitters for analysis {}", dead.size(), analysisId);
        }
    }

    public void complete(UUID analysisId) {
        List<SseEmitter> emitters = registry.remove(analysisId);
        if (emitters != null) {
            emitters.forEach(e -> {
                try { e.complete(); } catch (Exception ignored) {}
            });
        }
    }

    private void remove(UUID analysisId, SseEmitter emitter) {
        List<SseEmitter> list = registry.get(analysisId);
        if (list != null) list.remove(emitter);
    }
}
