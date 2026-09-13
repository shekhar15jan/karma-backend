package com.karmaos.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.memory.KarmaMemoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping(ApiConstants.VOICE)
public class VoiceController {

    private final VoiceService voiceService;
    private final VoiceChatService voiceChatService;
    private final VoiceTtsService voiceTtsService;
    private final KarmaMemoryService karmaMemoryService;
    private final ObjectMapper objectMapper;

    /** P5: dedicated pool for blocking chat runs behind SSE streams. */
    private final ExecutorService streamExec = Executors.newCachedThreadPool();

    public VoiceController(VoiceService voiceService, VoiceChatService voiceChatService,
                           VoiceTtsService voiceTtsService, KarmaMemoryService karmaMemoryService,
                           ObjectMapper objectMapper) {
        this.voiceService = voiceService;
        this.voiceChatService = voiceChatService;
        this.voiceTtsService = voiceTtsService;
        this.karmaMemoryService = karmaMemoryService;
        this.objectMapper = objectMapper;
    }

    private String extractRole(UserPrincipal user) {
        String role = "OPERATOR";
        if (user.getAuthorities() != null) {
            for (var authority : user.getAuthorities()) {
                String r = authority.getAuthority();
                if (r != null && r.startsWith("ROLE_")) {
                    role = r.substring(5);
                    break;
                }
            }
        }
        return role;
    }

    @PostMapping("/command")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processCommand(
            @RequestBody Map<String, Object> request) {
        String text = (String) request.getOrDefault("text", "");
        return ResponseEntity.ok(ApiResponse.ok(voiceService.processCommand(text)));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<VoiceChatResponse>> chat(
            @RequestBody VoiceChatRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        String role = extractRole(user);
        VoiceChatResponse response = voiceChatService.chat(request, user.getId(), role);
        UUID eventId = karmaMemoryService.recordInteraction(user.getId(), request.getText(), response,
                request.getCurrentPage());
        if (eventId != null) {
            String eventIdStr = eventId.toString();
            response.setEventId(eventIdStr);
            if (response.getAction() != null) {
                response.getAction().put("eventId", eventIdStr);
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * P5: SSE streaming chat. Emits `chunk` events (sentence pieces of the reply as they
     * are produced) followed by a single `final` event carrying the complete
     * VoiceChatResponse JSON (intent/action/actions/eventId). Falls back gracefully —
     * the client can always use POST /voice/chat.
     */
    @PostMapping(value = "/chat/stream")
    public SseEmitter chatStream(
            @RequestBody VoiceChatRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        String role = extractRole(user);
        UUID userId = user.getId();
        SseEmitter emitter = new SseEmitter(90_000L);

        streamExec.execute(() -> {
            try {
                VoiceChatResponse response = voiceChatService.chat(request, userId, role);
                UUID eventId = karmaMemoryService.recordInteraction(userId, request.getText(), response,
                        request.getCurrentPage());
                if (eventId != null) {
                    String eventIdStr = eventId.toString();
                    response.setEventId(eventIdStr);
                    if (response.getAction() != null) {
                        response.getAction().put("eventId", eventIdStr);
                    }
                    if (response.getActions() != null) {
                        for (Map<String, Object> a : response.getActions()) {
                            if (a != null) a.put("eventId", eventIdStr);
                        }
                    }
                }

                for (String part : splitSentences(response.getReply())) {
                    emitter.send(SseEmitter.event().name("chunk").data(part));
                }
                emitter.send(SseEmitter.event().name("final").data(objectMapper.writeValueAsString(response)));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("final").data(
                            "{\"reply\":\"Something went wrong. Please try again.\",\"intent\":\"chat\",\"target\":\"\"}"));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });
        return emitter;
    }

    /** Sentence splitter: emits chunks of >=60 chars, breaking on sentence enders incl. danda. */
    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] parts = text.split("(?<=[.!?।])\\s+");
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String p : parts) {
            buf.append(p).append(' ');
            if (buf.length() >= 60) {
                out.add(buf.toString().trim());
                buf.setLength(0);
            }
        }
        if (!buf.isEmpty()) out.add(buf.toString().trim());
        return out;
    }

    @PostMapping("/tts")
    public ResponseEntity<ApiResponse<VoiceTtsResponse>> tts(
            @RequestBody VoiceTtsRequest request) {
        VoiceTtsResponse response = voiceTtsService.synthesize(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/voices")
    public ResponseEntity<ApiResponse<Map<String, Object>>> voices() {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, String>> languages = new ArrayList<>();
        for (Map.Entry<String, String> e : voiceTtsService.getLanguageVoices().entrySet()) {
            Map<String, String> lang = new LinkedHashMap<>();
            lang.put("code", e.getKey());
            lang.put("name", voiceTtsService.languageName(e.getKey()));
            lang.put("voice", e.getValue());
            languages.add(lang);
        }
        body.put("languages", languages);
        body.put("voices", voiceTtsService.getAvailableVoices());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }
}
