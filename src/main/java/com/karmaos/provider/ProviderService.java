package com.karmaos.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.llm.LLMProvider;
import com.karmaos.llm.LLMProviderFactory;
import com.karmaos.llm.LLMRequest;
import com.karmaos.llm.LLMResponse;
import com.karmaos.media.ImageProviderFactory;
import com.karmaos.media.MusicProviderFactory;
import com.karmaos.media.VideoProviderFactory;
import com.karmaos.media.VoiceProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

private static final Set<String> SUPPORTED_PROVIDER_TYPES = Set.of(
            "google", "gemini", "groq", "openrouter", "opencode",
            "edge-tts", "edge", "ffmpeg", "google-music", "pollinations", "pollinations-image");

    private static final Set<String> LLM_TYPES = Set.of(
            "google", "gemini", "groq", "openrouter", "opencode");

    private static final Set<String> VOICE_TYPES = Set.of("edge-tts", "edge");

    private static final Set<String> IMAGE_TYPES = Set.of("google", "gemini", "pollinations", "pollinations-image");

    private static final Set<String> MUSIC_TYPES = Set.of("google-music");

    private static final Set<String> KEYLESS_TYPES = Set.of("edge-tts", "edge", "ffmpeg", "pollinations", "pollinations-image");

    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;
    private final ProviderKeyCipher keyCipher;
    private final LLMProviderFactory llmProviderFactory;
    private final ImageProviderFactory imageProviderFactory;
    private final VoiceProviderFactory voiceProviderFactory;
    private final VideoProviderFactory videoProviderFactory;
    private final MusicProviderFactory musicProviderFactory;

    public ProviderService(ProviderRepository providerRepository,
                           ObjectMapper objectMapper,
                           ProviderKeyCipher keyCipher,
                           LLMProviderFactory llmProviderFactory,
                           ImageProviderFactory imageProviderFactory,
                           VoiceProviderFactory voiceProviderFactory,
                           VideoProviderFactory videoProviderFactory,
                           MusicProviderFactory musicProviderFactory) {
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
        this.keyCipher = keyCipher;
        this.llmProviderFactory = llmProviderFactory;
        this.imageProviderFactory = imageProviderFactory;
        this.voiceProviderFactory = voiceProviderFactory;
        this.videoProviderFactory = videoProviderFactory;
        this.musicProviderFactory = musicProviderFactory;
    }

    public List<ProviderResponse> list() {
        return providerRepository.findAll()
                .stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !isPublishing(p))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProviderResponse getById(UUID id) {
        Provider provider = findActiveAiProvider(id);
        return toResponse(provider);
    }

    @Transactional
    public ProviderResponse create(ProviderRequest request) {
        String type = normalizeType(request.getProviderType());
        if (!SUPPORTED_PROVIDER_TYPES.contains(type)) {
            throw ErrorCode.BAD_REQUEST.exception("Unsupported provider type: " + request.getProviderType());
        }

        Provider provider = new Provider();
        provider.setName(request.getName().trim());
        provider.setProviderType(type);
        provider.setApiEndpoint(trimToNull(request.getApiEndpoint()));
        provider.setEncryptedApiKey(keyCipher.encrypt(request.getApiKey()));
        applyConfiguration(provider, request);
        try {
            java.util.List<String> modelsList = request.getModels() != null ? request.getModels() : java.util.List.of();
            provider.setModels(objectMapper.writeValueAsString(modelsList));
            provider.setCapabilities("[]");
        } catch (Exception e) {
            provider.setModels("[]");
            provider.setCapabilities("[]");
        }
        provider.setStatus("DISCONNECTED");
        provider = providerRepository.save(provider);
        return toResponse(provider);
    }

    @Transactional
    public ProviderResponse update(UUID id, ProviderRequest request) {
        Provider provider = findActiveAiProvider(id);

        String type = normalizeType(request.getProviderType());
        if (!SUPPORTED_PROVIDER_TYPES.contains(type)) {
            throw ErrorCode.BAD_REQUEST.exception("Unsupported provider type: " + request.getProviderType());
        }

        provider.setName(request.getName().trim());
        provider.setProviderType(type);
        provider.setApiEndpoint(trimToNull(request.getApiEndpoint()));
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            provider.setEncryptedApiKey(keyCipher.encrypt(request.getApiKey()));
        }
        applyConfiguration(provider, request);
        if (request.getModels() != null) {
            try {
                provider.setModels(objectMapper.writeValueAsString(request.getModels()));
            } catch (Exception ignored) {}
        }
        provider.setStatus("DISCONNECTED");
        evictProviderCaches();
        provider = providerRepository.save(provider);
        return toResponse(provider);
    }

    public Map<String, Object> testConnection(UUID id, String testApiKey, String testApiEndpoint) {
        Provider provider = findActiveAiProvider(id);

        String apiKey = (testApiKey != null && !testApiKey.isBlank())
                ? testApiKey.trim()
                : keyCipher.decrypt(provider.getEncryptedApiKey());
        String apiEndpoint = (testApiEndpoint != null && !testApiEndpoint.isBlank())
                ? testApiEndpoint.trim()
                : provider.getApiEndpoint();

        Map<String, Object> result = runConnectionTest(provider, apiKey, apiEndpoint);
        boolean ok = (Boolean) result.get("success");
        if (ok) {
            if (testApiKey != null && !testApiKey.isBlank()) {
                provider.setEncryptedApiKey(keyCipher.encrypt(testApiKey.trim()));
            }
            if (testApiEndpoint != null && !testApiEndpoint.isBlank()) {
                provider.setApiEndpoint(trimToNull(testApiEndpoint));
            }
        }
        provider.setStatus(ok ? "CONNECTED" : "ERROR");
        providerRepository.save(provider);
        Map<String, Object> out = new HashMap<>(result);
        out.put("status", provider.getStatus());
        return out;
    }

    /**
     * Runs the provider-specific connection probe without persisting. Returns a map of
     * success/message/modelCount so callers (Test and activate) behave identically.
     */
    private Map<String, Object> runConnectionTest(Provider provider, String apiKey, String apiEndpoint) {
        String type = normalizeType(provider.getProviderType());
        if (!SUPPORTED_PROVIDER_TYPES.contains(type)) {
            return Map.of("success", false, "message", "Unsupported provider type: " + provider.getProviderType(), "modelCount", 0);
        }

        try {
            boolean ok;
            String detail = "";
            if (hasImagenModel(provider) && !apiKey.isBlank()) {
                ok = imageProviderFactory.getProvider(type, apiKey).testConnection();
            } else if (MUSIC_TYPES.contains(type)) {
                if (apiKey.isBlank()) {
                    return Map.of("success", false, "message", "API key is required to test this provider", "modelCount", 0);
                }
                ok = musicProviderFactory.getProvider(type, apiKey).testConnection();
            } else if (LLM_TYPES.contains(type)) {
                if (apiKey.isBlank()) {
                    return Map.of("success", false, "message", "API key is required to test this provider", "modelCount", 0);
                }
                LLMProvider llmProvider = llmProviderFactory
                        .getProvider(type, apiKey, apiEndpoint);
                String probeModel = firstConfiguredModel(provider);
                LLMRequest probeRequest = new LLMRequest("You are a test bot.", "Reply with just: OK");
                probeRequest.setModel(probeModel != null ? probeModel : llmProvider.getDefaultModel());
                LLMResponse probeResponse = llmProvider.chat(probeRequest);
                ok = probeResponse != null && probeResponse.isSuccess();
                if (!ok && probeResponse != null && probeResponse.getErrorMessage() != null) {
                    String err = probeResponse.getErrorMessage().trim();
                    detail = err.length() > 250 ? err.substring(0, 250) + "..." : err;
                }
            } else if (VOICE_TYPES.contains(type)) {
                if (apiKey.isBlank() && !"edge-tts".equals(type) && !"edge".equals(type)) {
                    return Map.of("success", false, "message", "API key is required to test this provider", "modelCount", 0);
                }
                ok = voiceProviderFactory.getProvider(type, apiKey).testConnection();
            } else if (IMAGE_TYPES.contains(type)) {
                if (apiKey.isBlank() && !"pollinations".equals(type) && !"pollinations-image".equals(type)) {
                    return Map.of("success", false, "message", "API key is required to test this provider", "modelCount", 0);
                }
                ok = imageProviderFactory.getProvider(type, apiKey).testConnection();
            } else if ("ffmpeg".equals(type)) {
                ok = videoProviderFactory.getProvider(type, apiKey).testConnection();
            } else {
                return Map.of("success", false, "message", "Unsupported provider type", "modelCount", 0);
            }
            int syncedModels = 0;
            if (ok) {
                syncedModels = syncAvailableModels(provider, type, apiKey, apiEndpoint);
            }
            return Map.of(
                    "success", ok,
                    "message", ok ? "Connection successful" : ("Connection test failed" + (detail.isBlank() ? "" : ": " + detail)),
                    "modelCount", syncedModels);
        } catch (Exception e) {
            log.warn("Connection test failed for provider {}", provider.getName(), e);
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage(), "modelCount", 0);
        }
    }

    /**
     * After a successful connection test, fetch the live model list for the key and
     * persist it cheapest-first. Non-fatal: a failed sync never flips the status.
     */
    private int syncAvailableModels(Provider provider, String type, String apiKey, String apiEndpoint) {
        try {
            List<String> fetched = fetchModelsFor(provider, type, apiKey, apiEndpoint);
            if (fetched == null || fetched.isEmpty()) {
                return 0;
            }
            List<String> ordered = ModelRanker.orderCheapestFirst(fetched);
            provider.setModels(objectMapper.writeValueAsString(ordered));
            return ordered.size();
        } catch (Exception e) {
            log.warn("Model sync failed for provider {}: {}", provider.getName(), e.getMessage());
            return 0;
        }
    }

    private List<String> fetchModelsFor(Provider provider, String type, String apiKey, String apiEndpoint) {
        if ("ffmpeg".equals(type) || "edge".equals(type) || "edge-tts".equals(type)) {
            return null;
        }
        if (hasImagenModel(provider)) {
            return imageProviderFactory.getProvider(type, apiKey).fetchAvailableModels();
        }
        if (MUSIC_TYPES.contains(type)) {
            return musicProviderFactory.getProvider(type, apiKey).fetchAvailableModels();
        }
        if (LLM_TYPES.contains(type)) {
            return llmProviderFactory.getProvider(type, apiKey, apiEndpoint).fetchAvailableModels();
        }
        if (VOICE_TYPES.contains(type)) {
            return voiceProviderFactory.getProvider(type, apiKey).fetchAvailableModels();
        }
        if (IMAGE_TYPES.contains(type)) {
            return imageProviderFactory.getProvider(type, apiKey).fetchAvailableModels();
        }
        return null;
    }

    @Transactional
    public void delete(UUID id) {
        Provider provider = findActiveAiProvider(id);
        evictProviderCaches();
        provider.softDelete();
        providerRepository.save(provider);
    }

    @Transactional
    public void activate(UUID id) {
        Provider provider = findActiveAiProvider(id);
        String type = normalizeType(provider.getProviderType());
        if (keyRequired(type) && !hasKey(provider)) {
            throw ErrorCode.BAD_REQUEST.exception("Configure an API key before connecting this provider");
        }
        String apiKey = keyCipher.decrypt(provider.getEncryptedApiKey());
        String apiEndpoint = provider.getApiEndpoint();
        Map<String, Object> result = runConnectionTest(provider, apiKey, apiEndpoint);
        if (!(Boolean) result.get("success")) {
            provider.setStatus("ERROR");
            providerRepository.save(provider);
            evictProviderCaches();
            throw ErrorCode.BAD_REQUEST.exception((String) result.get("message"));
        }
        provider.setStatus("CONNECTED");
        evictProviderCaches();
        providerRepository.save(provider);
    }

    private boolean hasKey(Provider p) {
        byte[] key = p.getEncryptedApiKey();
        return key != null && key.length > 0;
    }

    private boolean keyRequired(String type) {
        return !KEYLESS_TYPES.contains(normalizeType(type));
    }

    @Transactional
    public void deactivate(UUID id) {
        Provider provider = findActiveAiProvider(id);
        provider.setStatus("DISCONNECTED");
        evictProviderCaches();
        providerRepository.save(provider);
    }

    private Provider findActiveAiProvider(UUID id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Provider not found: " + id));
        if (provider.getDeletedAt() != null) {
            throw ErrorCode.NOT_FOUND.exception("Provider not found: " + id);
        }
        if (isPublishing(provider)) {
            throw ErrorCode.BAD_REQUEST.exception("Publishing channels are managed via the Publishing Center");
        }
        return provider;
    }

    private void evictProviderCaches() {
        llmProviderFactory.evictAll();
        imageProviderFactory.evictAll();
        voiceProviderFactory.evictAll();
        videoProviderFactory.evictAll();
        musicProviderFactory.evictAll();
    }

    private boolean isPublishing(Provider p) {
        return "PUBLISHING".equalsIgnoreCase(p.getProviderType());
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstConfiguredModel(Provider provider) {
        try {
            List<String> models = objectMapper.readValue(provider.getModels(), new TypeReference<List<String>>() {});
            if (models != null) {
                for (String m : models) {
                    if (m != null && !m.isBlank()) {
                        return m.trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean hasImagenModel(Provider provider) {
        try {
            List<String> models = objectMapper.readValue(provider.getModels(), new TypeReference<List<String>>() {});
            if (models != null) {
                for (String m : models) {
                    if (m != null && m.toLowerCase(Locale.ROOT).contains("imagen")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return provider.getName() != null && provider.getName().toLowerCase(Locale.ROOT).contains("imagen");
    }

    private void applyConfiguration(Provider provider, ProviderRequest request) {
        if (request.getConfiguration() == null) {
            return;
        }
        try {
            provider.setConfiguration(objectMapper.writeValueAsString(request.getConfiguration()));
        } catch (Exception e) {
            throw ErrorCode.BAD_REQUEST.exception("Invalid configuration payload");
        }
    }

    private ProviderResponse toResponse(Provider p) {
        List<String> models = Collections.emptyList();
        List<String> capabilities = Collections.emptyList();
        try {
            models = objectMapper.readValue(p.getModels(), new TypeReference<List<String>>() {});
            capabilities = objectMapper.readValue(p.getCapabilities(), new TypeReference<List<String>>() {});
        } catch (Exception ignored) {}

        return ProviderResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .providerType(p.getProviderType())
                .apiEndpoint(p.getApiEndpoint())
                .models(models)
                .capabilities(capabilities)
                .status(p.getStatus())
                .apiKeyConfigured(hasKey(p))
                .keyRequired(keyRequired(p.getProviderType()))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
