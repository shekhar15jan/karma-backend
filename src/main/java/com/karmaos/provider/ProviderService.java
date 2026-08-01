package com.karmaos.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.llm.LLMProviderFactory;
import com.karmaos.media.ImageProviderFactory;
import com.karmaos.media.VideoProviderFactory;
import com.karmaos.media.VoiceProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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
            "openai", "anthropic", "google", "gemini", "groq", "openrouter", "opencode",
            "stability", "elevenlabs", "edge-tts", "edge", "ffmpeg");

    private static final Set<String> LLM_TYPES = Set.of(
            "openai", "anthropic", "google", "gemini", "groq", "openrouter", "opencode");

    private static final Set<String> VOICE_TYPES = Set.of("elevenlabs", "edge-tts", "edge");

    private static final Set<String> IMAGE_TYPES = Set.of("stability", "google", "gemini", "openai");

    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;
    private final ProviderKeyCipher keyCipher;
    private final LLMProviderFactory llmProviderFactory;
    private final ImageProviderFactory imageProviderFactory;
    private final VoiceProviderFactory voiceProviderFactory;
    private final VideoProviderFactory videoProviderFactory;

    public ProviderService(ProviderRepository providerRepository,
                           ObjectMapper objectMapper,
                           ProviderKeyCipher keyCipher,
                           LLMProviderFactory llmProviderFactory,
                           ImageProviderFactory imageProviderFactory,
                           VoiceProviderFactory voiceProviderFactory,
                           VideoProviderFactory videoProviderFactory) {
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
        this.keyCipher = keyCipher;
        this.llmProviderFactory = llmProviderFactory;
        this.imageProviderFactory = imageProviderFactory;
        this.voiceProviderFactory = voiceProviderFactory;
        this.videoProviderFactory = videoProviderFactory;
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

    public Map<String, Object> testConnection(UUID id) {
        Provider provider = findActiveAiProvider(id);

        String type = normalizeType(provider.getProviderType());
        if (!SUPPORTED_PROVIDER_TYPES.contains(type)) {
            return Map.of("success", false, "message", "Unsupported provider type: " + provider.getProviderType());
        }

        String apiKey = keyCipher.decrypt(provider.getEncryptedApiKey());

        try {
            boolean ok;
            String detail = "";
            if (LLM_TYPES.contains(type)) {
                if (apiKey.isBlank()) {
                    return fail(provider, "API key is required to test this provider");
                }
                ok = llmProviderFactory
                        .getProvider(type, apiKey, provider.getApiEndpoint())
                        .testConnection();
            } else if (VOICE_TYPES.contains(type)) {
                if (apiKey.isBlank() && !"edge-tts".equals(type) && !"edge".equals(type)) {
                    return fail(provider, "API key is required to test this provider");
                }
                ok = voiceProviderFactory.getProvider(type, apiKey).testConnection();
            } else if (IMAGE_TYPES.contains(type)) {
                if (apiKey.isBlank()) {
                    return fail(provider, "API key is required to test this provider");
                }
                ok = imageProviderFactory.getProvider(type, apiKey).testConnection();
            } else if ("ffmpeg".equals(type)) {
                ok = videoProviderFactory.getProvider(type, apiKey).testConnection();
            } else {
                ok = false;
                detail = "Unsupported provider type";
            }
            provider.setStatus(ok ? "CONNECTED" : "ERROR");
            providerRepository.save(provider);
            return Map.of(
                    "success", ok,
                    "message", ok ? "Connection successful" : ("Connection test failed" + (detail.isBlank() ? "" : ": " + detail))
            );
        } catch (Exception e) {
            log.warn("Connection test failed for provider {}", provider.getName(), e);
            provider.setStatus("ERROR");
            providerRepository.save(provider);
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(UUID id) {
        Provider provider = findActiveAiProvider(id);
        evictProviderCaches();
        provider.softDelete();
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
    }

    private Map<String, Object> fail(Provider provider, String message) {
        provider.setStatus("ERROR");
        providerRepository.save(provider);
        return Map.of("success", false, "message", message);
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
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
