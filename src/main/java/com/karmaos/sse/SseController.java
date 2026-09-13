package com.karmaos.sse;

import com.karmaos.auth.UserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SseController {

    private final Map<UUID, Map<String, SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal UserPrincipal user) {
        UUID userId = user.getId();
        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emittersByUser.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(clientId, emitter);

        Runnable cleanup = () -> {
            Map<String, SseEmitter> userEmitters = emittersByUser.get(userId);
            if (userEmitters != null) {
                userEmitters.remove(clientId);
                if (userEmitters.isEmpty()) {
                    emittersByUser.remove(userId);
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"clientId\":\"" + clientId + "\"}"));
        } catch (Exception e) {
            cleanup.run();
        }

        return emitter;
    }

    public void sendEvent(String eventType, String data) {
        for (Map<String, SseEmitter> userEmitters : emittersByUser.values()) {
            for (Map.Entry<String, SseEmitter> entry : userEmitters.entrySet()) {
                try {
                    entry.getValue().send(SseEmitter.event().name(eventType).data(data));
                } catch (Exception e) {
                    userEmitters.remove(entry.getKey());
                }
            }
        }
    }

    public void sendEvent(UUID userId, String eventType, String data) {
        Map<String, SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null) {
            return;
        }
        for (Map.Entry<String, SseEmitter> entry : userEmitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name(eventType).data(data));
            } catch (Exception e) {
                userEmitters.remove(entry.getKey());
            }
        }
        if (userEmitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }

    public void sendEvent(String clientId, String eventType, String data) {
        for (Map<String, SseEmitter> userEmitters : emittersByUser.values()) {
            SseEmitter emitter = userEmitters.get(clientId);
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().name(eventType).data(data));
                } catch (Exception e) {
                    userEmitters.remove(clientId);
                }
                return;
            }
        }
    }
}