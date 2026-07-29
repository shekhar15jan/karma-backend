package com.karmaos.config;

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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.CONFIG)
public class ConfigController {

    private final SettingsService settingsService;

    public ConfigController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) UUID userId) {
        try {
            SettingsResponse settings = settingsService.getByWorkspaceAndUser(workspaceId, userId);
            return ResponseEntity.ok(ApiResponse.ok(
                    Map.of("settings", settings.getSettingsData(),
                            "theme", "dark",
                            "language", "en-US",
                            "notifications", Map.of(
                                    "missionComplete", true,
                                    "missionFailed", true,
                                    "reviewRequired", true,
                                    "publishComplete", true))));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "theme", "dark",
                    "language", "en-US",
                    "notifications", Map.of(
                            "missionComplete", true,
                            "missionFailed", true,
                            "reviewRequired", true,
                            "publishComplete", true))));
        }
    }

    @PutMapping
    public ResponseEntity<ApiResponse<SettingsResponse>> updateConfig(
            @RequestBody Map<String, Object> config,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) UUID userId) {
        SettingsRequest request = SettingsRequest.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .settingsData(config.toString())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(settingsService.save(request)));
    }
}
