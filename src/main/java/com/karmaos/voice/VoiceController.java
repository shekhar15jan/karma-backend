package com.karmaos.voice;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.VOICE)
public class VoiceController {

    private final VoiceService voiceService;
    private final VoiceChatService voiceChatService;
    private final VoiceTtsService voiceTtsService;

    public VoiceController(VoiceService voiceService, VoiceChatService voiceChatService,
                           VoiceTtsService voiceTtsService) {
        this.voiceService = voiceService;
        this.voiceChatService = voiceChatService;
        this.voiceTtsService = voiceTtsService;
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
        VoiceChatResponse response = voiceChatService.chat(request, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(response));
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
