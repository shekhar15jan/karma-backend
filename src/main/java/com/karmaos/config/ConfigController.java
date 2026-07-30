package com.karmaos.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.settings.SettingsRequest;
import com.karmaos.settings.SettingsResponse;
import com.karmaos.settings.SettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.CONFIG)
public class ConfigController {

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;

    public ConfigController(SettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) UUID userId) {
        try {
            SettingsResponse settings = settingsService.getByWorkspaceAndUser(workspaceId, userId);
            Map<String, Object> data = Collections.emptyMap();
            if (settings != null && settings.getSettingsData() != null) {
                try {
                    data = objectMapper.readValue(settings.getSettingsData(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception ignored) {}
            }
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyMap()));
        }
    }

    @PutMapping
    public ResponseEntity<ApiResponse<SettingsResponse>> updateConfig(
            @RequestBody Map<String, Object> config,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) UUID userId) {
        try {
            String json = objectMapper.writeValueAsString(config);
            SettingsRequest request = SettingsRequest.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .settingsData(json)
                    .build();
            return ResponseEntity.ok(ApiResponse.ok(settingsService.save(request)));
        } catch (Exception e) {
            SettingsRequest request = SettingsRequest.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .settingsData(config.toString())
                    .build();
            return ResponseEntity.ok(ApiResponse.ok(settingsService.save(request)));
        }
    }
}
