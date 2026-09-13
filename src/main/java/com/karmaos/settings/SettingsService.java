package com.karmaos.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SettingsService {

    private final SettingsRepository repository;
    private final ObjectMapper objectMapper;

    public SettingsService(SettingsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Default settings surfaced to the frontend when the key is not stored yet.
     * These are merged into every read so they appear in the Admin > Settings tab
     * and can be overridden from the UI.
     */
    private static final Map<String, Object> DEFAULT_SETTINGS = Map.of(
        RuntimeSettings.KEY_MAX_TOKENS_DEFAULT, 1024,
        RuntimeSettings.KEY_MAX_TOKENS_REASONING, 4096,
        RuntimeSettings.KEY_MAX_TOKENS_SCRIPT, 8192,
        RuntimeSettings.KEY_MUSIC_MAX_DURATION, 30,
        RuntimeSettings.KEY_VIDEO_TARGET_DURATION, 900,
        RuntimeSettings.KEY_VIDEO_MIN_SCENES, 30,
        RuntimeSettings.KEY_VIDEO_MAX_SCENES, 36,
        RuntimeSettings.KEY_VIDEO_WORDS_PER_SCENE, 70,
        RuntimeSettings.KEY_VIDEO_CHUNK_DELAY_MS, 4000
    );

    public SettingsResponse getByWorkspaceAndUser(UUID workspaceId, UUID userId) {
        Optional<Settings> existing = find(workspaceId, userId);
        return existing.map(this::toResponse)
                .orElseGet(() -> SettingsResponse.builder().settingsData(mergeDefaults(null)).build());
    }

    private Optional<Settings> find(UUID workspaceId, UUID userId) {
        if (workspaceId == null && userId == null) {
            return repository.findFirstByOrderByUpdatedAtDesc();
        }
        Optional<Settings> existing = repository.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (existing.isEmpty() && workspaceId != null) {
            existing = repository.findByWorkspaceId(workspaceId);
        }
        if (existing.isEmpty() && userId != null) {
            existing = repository.findByUserId(userId);
        }
        return existing;
    }

    @Transactional
    public SettingsResponse save(SettingsRequest request) {
        Optional<Settings> existing = request.getWorkspaceId() == null && request.getUserId() == null
                ? repository.findFirstByOrderByUpdatedAtDesc()
                : repository.findByWorkspaceIdAndUserId(request.getWorkspaceId(), request.getUserId());
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
        find(workspaceId, userId)
                .ifPresent(repository::delete);
    }

    private SettingsResponse toResponse(Settings s) {
        return SettingsResponse.builder()
                .id(s.getId())
                .workspaceId(s.getWorkspaceId())
                .userId(s.getUserId())
                .settingsData(mergeDefaults(s.getSettingsData()))
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private String mergeDefaults(String settingsData) {
        try {
            Map<String, Object> data = (settingsData == null || settingsData.isBlank())
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(settingsData, new TypeReference<Map<String, Object>>() {});
            DEFAULT_SETTINGS.forEach(data::putIfAbsent);
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return settingsData == null ? "{}" : settingsData;
        }
    }
}
