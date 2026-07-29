package com.karmaos.sse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SseController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam(defaultValue = "") String token) {
        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(clientId, emitter);

        emitter.onCompletion(() -> emitters.remove(clientId));
        emitter.onTimeout(() -> emitters.remove(clientId));
        emitter.onError(e -> emitters.remove(clientId));

        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"clientId\":\"" + clientId + "\"}"));
        } catch (Exception e) {
            emitters.remove(clientId);
        }

        return emitter;
    }

    public void sendEvent(String eventType, String data) {
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name(eventType).data(data));
            } catch (Exception e) {
                emitters.remove(entry.getKey());
            }
        }
    }

    public void sendEvent(String clientId, String eventType, String data) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(data));
            } catch (Exception e) {
                emitters.remove(clientId);
            }
        }
    }
}
