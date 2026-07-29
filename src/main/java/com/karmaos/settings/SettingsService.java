package com.karmaos.settings;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class SettingsService {

    private final SettingsRepository repository;

    public SettingsService(SettingsRepository repository) {
        this.repository = repository;
    }

    public SettingsResponse getByWorkspaceAndUser(UUID workspaceId, UUID userId) {
        Optional<Settings> existing = repository.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (existing.isEmpty() && workspaceId != null) {
            existing = repository.findByWorkspaceId(workspaceId);
        }
        if (existing.isEmpty() && userId != null) {
            existing = repository.findByUserId(userId);
        }
        return existing.map(this::toResponse)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Settings not found"));
    }

    @Transactional
    public SettingsResponse save(SettingsRequest request) {
        Optional<Settings> existing = repository.findByWorkspaceIdAndUserId(
                request.getWorkspaceId(), request.getUserId());
        Settings settings;
        if (existing.isPresent()) {
            settings = existing.get();
        } else {
            settings = new Settings();
            settings.setWorkspaceId(request.getWorkspaceId());
            settings.setUserId(request.getUserId());
        }
        settings.setSettingsData(request.getSettingsData());
        settings = repository.save(settings);
        return toResponse(settings);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID userId) {
        repository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .ifPresent(repository::delete);
    }

    private SettingsResponse toResponse(Settings s) {
        return SettingsResponse.builder()
                .id(s.getId())
                .workspaceId(s.getWorkspaceId())
                .userId(s.getUserId())
                .settingsData(s.getSettingsData())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
