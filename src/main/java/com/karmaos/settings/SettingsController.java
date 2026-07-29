package com.karmaos.settings;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.SETTINGS)
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SettingsResponse>> get(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.getByWorkspaceAndUser(workspaceId, userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<SettingsResponse>> save(@Valid @RequestBody SettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.save(request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) UUID userId) {
        settingsService.delete(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Settings deleted"));
    }
}
