package com.karmaos.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
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
        if (request.getApiKey() != null) {
            provider.setEncryptedApiKey(request.getApiKey().getBytes());
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
        if (request.getApiKey() != null) {
            provider.setEncryptedApiKey(request.getApiKey().getBytes());
        }
        provider = providerRepository.save(provider);
        return toResponse(provider);
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
