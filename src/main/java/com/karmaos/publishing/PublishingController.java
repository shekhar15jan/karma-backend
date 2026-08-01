package com.karmaos.publishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.access.OwnershipService;
import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.publication.Publication;
import com.karmaos.publication.PublicationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiConstants.API_V1 + "/publishing")
public class PublishingController {

    private final ArtifactRepository artifactRepository;
    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final PublicationRepository publicationRepository;
    private final ObjectMapper objectMapper;
    private final OwnershipService ownershipService;

    public PublishingController(ArtifactRepository artifactRepository,
                                ProviderRepository providerRepository,
                                ProviderKeyCipher providerKeyCipher,
                                PublicationRepository publicationRepository,
                                ObjectMapper objectMapper,
                                OwnershipService ownershipService) {
        this.artifactRepository = artifactRepository;
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.publicationRepository = publicationRepository;
        this.objectMapper = objectMapper;
        this.ownershipService = ownershipService;
    }

    @GetMapping("/channels")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> channels() {
        List<Provider> channels = providerRepository.findByProviderType("PUBLISHING").stream()
                .filter(p -> p.getDeletedAt() == null)
                .toList();
        var result = channels.stream()
                .map(this::toChannel)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/channels/custom")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCustomChannel(
            @RequestBody Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("Channel name is required", 400));
        }

        Provider p = new Provider();
        p.setName(name.trim());
        p.setProviderType("PUBLISHING");
        p.setApiEndpoint(str(body.get("endpoint")));
        String token = str(body.get("authToken"));
        if (token != null && !token.isBlank()) {
            p.setEncryptedApiKey(providerKeyCipher.encrypt(token));
        }
        applyCustomConfig(p, body);
        p.setStatus(p.getApiEndpoint() != null && !p.getApiEndpoint().isBlank() ? "CONNECTED" : "DISCONNECTED");
        p = providerRepository.save(p);
        return ResponseEntity.ok(ApiResponse.ok(toChannel(p)));
    }

    @PutMapping("/channels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCustomChannel(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Optional<Provider> optional = providerRepository.findById(id);
        if (optional.isEmpty() || optional.get().getDeletedAt() != null) {
            return ResponseEntity.ok(ApiResponse.error("Channel not found", 404));
        }
        Provider p = optional.get();
        if (!isCustom(p)) {
            return ResponseEntity.ok(ApiResponse.error("Only custom channels can be edited", 400));
        }
        String name = str(body.get("name"));
        if (name != null && !name.isBlank()) {
            p.setName(name.trim());
        }
        String endpoint = str(body.get("endpoint"));
        if (endpoint != null) {
            p.setApiEndpoint(endpoint.isBlank() ? null : endpoint.trim());
        }
        String token = str(body.get("authToken"));
        if (token != null && !token.isBlank()) {
            p.setEncryptedApiKey(providerKeyCipher.encrypt(token));
        }
        applyCustomConfig(p, body);
        p.setStatus(p.getApiEndpoint() != null && !p.getApiEndpoint().isBlank() ? "CONNECTED" : "DISCONNECTED");
        p = providerRepository.save(p);
        return ResponseEntity.ok(ApiResponse.ok(toChannel(p)));
    }

    @DeleteMapping("/channels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteCustomChannel(@PathVariable UUID id) {
        Optional<Provider> optional = providerRepository.findById(id);
        if (optional.isEmpty() || optional.get().getDeletedAt() != null) {
            return ResponseEntity.ok(ApiResponse.error("Channel not found", 404));
        }
        Provider p = optional.get();
        if (!isCustom(p)) {
            return ResponseEntity.ok(ApiResponse.error("Built-in channels cannot be removed", 400));
        }
        p.softDelete();
        providerRepository.save(p);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id.toString(), "deleted", true)));
    }

    @PutMapping("/channels/{id}/connect")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> connect(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        Optional<Provider> provider = providerRepository.findById(id);
        if (provider.isEmpty() || provider.get().getDeletedAt() != null) {
            return ResponseEntity.ok(ApiResponse.error("Channel not found", 404));
        }
        Provider p = provider.get();
        p.setStatus("CONNECTED");
        if (body != null && body.get("handle") != null && !body.get("handle").isBlank()) {
            p.setApiEndpoint(body.get("handle"));
        }
        if (body != null && body.get("authKey") != null && !body.get("authKey").isBlank()) {
            p.setEncryptedApiKey(providerKeyCipher.encrypt(body.get("authKey")));
        }
        providerRepository.save(p);
        return ResponseEntity.ok(ApiResponse.ok(toChannel(p)));
    }

    @PutMapping("/channels/{id}/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disconnect(@PathVariable UUID id) {
        Optional<Provider> provider = providerRepository.findById(id);
        if (provider.isEmpty() || provider.get().getDeletedAt() != null) {
            return ResponseEntity.ok(ApiResponse.error("Channel not found", 404));
        }
        Provider p = provider.get();
        p.setStatus("DISCONNECTED");
        if (!isCustom(p)) {
            p.setApiEndpoint(null);
        }
        providerRepository.save(p);
        return ResponseEntity.ok(ApiResponse.ok(toChannel(p)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> logs(
            @AuthenticationPrincipal UserPrincipal user) {
        List<UUID> missionIds = ownershipService.getOwnedMissionIds(user.getId());
        List<Artifact> artifacts = missionIds.isEmpty()
                ? List.of()
                : artifactRepository.findByMissionIdInOrderByCreatedAtDesc(missionIds);
        if (artifacts.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        Map<UUID, Artifact> artifactById = artifacts.stream()
                .collect(Collectors.toMap(Artifact::getId, a -> a, (a, b) -> a));
        List<Publication> publications = publicationRepository
                .findByArtifactIdInOrderByCreatedAtDesc(artifactById.keySet());

        List<Map<String, Object>> logs = new ArrayList<>();
        for (Publication pub : publications) {
            Artifact a = artifactById.get(pub.getArtifactId());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", pub.getId().toString());
            entry.put("title", a != null && a.getName() != null ? a.getName() : "Artifact");
            entry.put("platform", pub.getDestination());
            entry.put("status", mapLogStatus(pub.getStatus()));
            entry.put("published_at", pub.getPublishedAt() != null
                    ? pub.getPublishedAt().toString()
                    : (pub.getCreatedAt() != null ? pub.getCreatedAt().toString() : null));
            entry.put("views", 0);
            if (pub.getUrl() != null) {
                entry.put("url", pub.getUrl());
            }
            if (pub.getErrorMessage() != null) {
                entry.put("error", pub.getErrorMessage());
            }
            logs.add(entry);
        }
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

    private String mapLogStatus(String status) {
        if (status == null) return "pending";
        return switch (status) {
            case "COMPLETED", "PUBLISHED" -> "completed";
            case "FAILED" -> "failed";
            default -> "pending";
        };
    }

    private Map<String, Object> toChannel(Provider p) {
        JsonNode config = readConfig(p);
        boolean custom = config.hasNonNull("custom") && config.get("custom").asBoolean(false);
        String platform = config.hasNonNull("platform") && !config.get("platform").asText("").isBlank()
                ? config.get("platform").asText()
                : p.getName().toLowerCase();
        String authType = config.hasNonNull("authType") ? config.get("authType").asText() : "OAuth";
        String contentFormat = config.hasNonNull("contentFormat") ? config.get("contentFormat").asText() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", p.getId().toString());
        result.put("name", p.getName());
        result.put("platform", platform);
        result.put("handle", p.getApiEndpoint() != null ? p.getApiEndpoint() : "");
        result.put("status", "CONNECTED".equals(p.getStatus()) ? "connected" : "disconnected");
        result.put("custom", custom);
        result.put("authType", authType);
        result.put("contentFormat", contentFormat);
        result.put("authConfigured", providerKeyCipher.decrypt(p.getEncryptedApiKey()) != null
                && !providerKeyCipher.decrypt(p.getEncryptedApiKey()).isBlank());
        return result;
    }

    private void applyCustomConfig(Provider p, Map<String, Object> body) {
        String platform = str(body.get("platform"));
        String authType = str(body.get("authType"));
        String contentFormat = str(body.get("contentFormat"));
        String notes = str(body.get("notes"));
        try {
            p.setCapabilities("[\"PUBLISHING\"]");
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("custom", true);
            config.put("platform", platform != null && !platform.isBlank() ? platform.trim() : "custom");
            config.put("authType", authType != null && !authType.isBlank() ? authType : "Bearer");
            config.put("contentFormat", contentFormat != null && !contentFormat.isBlank() ? contentFormat : "text/markdown");
            if (notes != null && !notes.isBlank()) {
                config.put("notes", notes);
            }
            p.setConfiguration(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            p.setCapabilities("[]");
            p.setConfiguration("{\"custom\":true,\"platform\":\"custom\",\"authType\":\"Bearer\",\"contentFormat\":\"text/markdown\"}");
        }
    }

    private boolean isCustom(Provider p) {
        JsonNode config = readConfig(p);
        return config.hasNonNull("custom") && config.get("custom").asBoolean(false);
    }

    private JsonNode readConfig(Provider p) {
        if (p.getConfiguration() == null || p.getConfiguration().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(p.getConfiguration());
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
