package com.karmaos.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Runtime access to global (workspace/user-agnostic) settings stored as a JSON blob,
 * merged with built-in defaults. Used by agent/media execution to honour
 * frontend-configurable budget controls.
 */
@Component
public class RuntimeSettings {

    public static final String KEY_MAX_TOKENS_DEFAULT = "llm.max_tokens.default";
    public static final String KEY_MAX_TOKENS_REASONING = "llm.max_tokens.reasoning";
    public static final String KEY_MAX_TOKENS_SCRIPT = "llm.max_tokens.script";
    public static final String KEY_MUSIC_MAX_DURATION = "music.max_duration_seconds";
    public static final String KEY_GOOGLE_SEARCH_GROUNDING = "llm.google_search_grounding";

    public static final String KEY_VIDEO_TARGET_DURATION = "video.target_duration_seconds";
    public static final String KEY_VIDEO_MIN_SCENES = "video.min_scenes";
    public static final String KEY_VIDEO_MAX_SCENES = "video.max_scenes";
    public static final String KEY_VIDEO_WORDS_PER_SCENE = "video.words_per_scene";
    public static final String KEY_VIDEO_FFMPEG_TIMEOUT = "video.ffmpeg_timeout_seconds";

    private final SettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    public RuntimeSettings(SettingsRepository settingsRepository, ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.objectMapper = objectMapper;
    }

    public int getInt(String key, int defaultValue) {
        Object value = global().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = global().get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String str && !str.isBlank()) {
            if ("true".equalsIgnoreCase(str.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(str.trim())) {
                return false;
            }
        }
        return defaultValue;
    }

    public Map<String, Object> global() {
        try {
            return settingsRepository.findFirstByOrderByUpdatedAtDesc()
                    .filter(s -> s.getSettingsData() != null)
                    .map(s -> {
                        try {
                            return objectMapper.readValue(s.getSettingsData(), new TypeReference<Map<String, Object>>() {});
                        } catch (Exception e) {
                            return Map.<String, Object>of();
                        }
                    })
                    .orElseGet(Map::of);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
