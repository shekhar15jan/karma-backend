package com.karmaos.voice;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(ApiConstants.VOICE)
public class VoiceController {

    private final VoiceService voiceService;

    public VoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    @PostMapping("/command")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processCommand(
            @RequestBody Map<String, Object> request) {
        String text = (String) request.getOrDefault("text", "");
        return ResponseEntity.ok(ApiResponse.ok(voiceService.processCommand(text)));
    }
}
