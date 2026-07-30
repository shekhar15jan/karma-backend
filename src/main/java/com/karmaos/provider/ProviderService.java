package com.karmaos.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karmaos.media.EdgeTtsProvider;
import com.karmaos.media.VoiceProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;

    public ProviderService(ProviderRepository providerRepository, ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
    }

    public List<ProviderResponse> list() {
        return providerRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProviderResponse getById(UUID id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Provider not found: " + id));
        return toResponse(provider);
    }

    @Transactional
    public ProviderResponse create(ProviderRequest request) {
        Provider provider = new Provider();
        provider.setName(request.getName());
        provider.setProviderType(request.getProviderType());
        provider.setApiEndpoint(request.getApiEndpoint());
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            provider.setEncryptedApiKey(request.getApiKey().getBytes());
        }
        // Serialize models list to JSON; default to empty array
        try {
            java.util.List<String> modelsList = request.getModels() != null ? request.getModels() : java.util.List.of();
            provider.setModels(objectMapper.writeValueAsString(modelsList));
            provider.setCapabilities("[]");
        } catch (Exception e) {
            provider.setModels("[]");
            provider.setCapabilities("[]");
        }
        provider.setStatus("CONNECTED");
        provider = providerRepository.save(provider);
        return toResponse(provider);
    }

    @Transactional
    public ProviderResponse update(UUID id, ProviderRequest request) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Provider not found: " + id));
        provider.setName(request.getName());
        provider.setProviderType(request.getProviderType());
        provider.setApiEndpoint(request.getApiEndpoint());
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            provider.setEncryptedApiKey(request.getApiKey().getBytes());
        }
        if (request.getModels() != null) {
            try {
                provider.setModels(objectMapper.writeValueAsString(request.getModels()));
            } catch (Exception ignored) {}
        }
        provider = providerRepository.save(provider);
        return toResponse(provider);
    }

    private static final Set<String> CLI_BASED_PROVIDERS = Set.of("edge-tts", "edge", "ffmpeg");

    public Map<String, Object> testConnection(UUID id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Provider not found: " + id));

        String type = provider.getProviderType().toLowerCase();

        if (CLI_BASED_PROVIDERS.contains(type)) {
            return testCliProvider(provider, type);
        }

        String endpoint = provider.getApiEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return Map.of("success", false, "message", "No API endpoint configured");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            boolean success = status >= 200 && status < 500;
            provider.setStatus(success ? "CONNECTED" : "ERROR");
            providerRepository.save(provider);
            return Map.of(
                    "success", success,
                    "status", status,
                    "message", success ? "Connection successful" : "Received status " + status
            );
        } catch (Exception e) {
            provider.setStatus("ERROR");
            providerRepository.save(provider);
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    private Map<String, Object> testCliProvider(Provider provider, String type) {
        try {
            VoiceProvider voiceProvider = switch (type) {
                case "edge-tts", "edge" -> new EdgeTtsProvider();
                default -> null;
            };
            if (voiceProvider == null) {
                return Map.of("success", false, "message", "Unsupported CLI provider");
            }
            boolean ok = voiceProvider.testConnection();
            provider.setStatus(ok ? "CONNECTED" : "ERROR");
            providerRepository.save(provider);
            return Map.of(
                    "success", ok,
                    "message", ok ? "Connection successful" : "CLI provider test failed"
            );
        } catch (Exception e) {
            provider.setStatus("ERROR");
            providerRepository.save(provider);
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(UUID id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Provider not found: " + id));
        provider.softDelete();
        providerRepository.save(provider);
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
