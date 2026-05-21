package com.taskflow.service;

import com.taskflow.agent.TaskProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    private static final long SSE_TIMEOUT_MS = 300_000;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> emitters.remove(taskId));
        emitter.onError(e -> {
            log.warn("SSE emitter error for task {}: {}", taskId, e.getMessage());
            emitters.remove(taskId);
        });

        emitters.put(taskId, emitter);
        return emitter;
    }

    public void publish(TaskProgressEvent event) {
        SseEmitter emitter = emitters.get(event.taskId());
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name(event.type().name())
                    .data(event));
        } catch (IOException e) {
            log.warn("Failed to send SSE event for task {}: {}", event.taskId(), e.getMessage());
            emitters.remove(event.taskId());
        }
    }

    public void complete(String taskId) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to complete SSE emitter for task {}", taskId);
            }
        }
    }
}
