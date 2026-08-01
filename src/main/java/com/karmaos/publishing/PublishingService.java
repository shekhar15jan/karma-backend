package com.karmaos.publishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.publication.Publication;
import com.karmaos.publication.PublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real publishing pipeline: publishes an approved artifact to every CONNECTED PUBLISHING
 * channel and records one {@link Publication} row per destination. Used both from the
 * artifact approval flow (ExecutionOrchestrator.onArtifactApproved) and the publish step.
 */
@Service
public class PublishingService {

    private static final Logger log = LoggerFactory.getLogger(PublishingService.class);

    private final ArtifactRepository artifactRepository;
    private final MissionRepository missionRepository;
    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final SocialPublisherFactory socialPublisherFactory;
    private final PublicationRepository publicationRepository;
    private final ObjectMapper objectMapper;

    public PublishingService(ArtifactRepository artifactRepository,
                             MissionRepository missionRepository,
                             ProviderRepository providerRepository,
                             ProviderKeyCipher providerKeyCipher,
                             SocialPublisherFactory socialPublisherFactory,
                             PublicationRepository publicationRepository,
                             ObjectMapper objectMapper) {
        this.artifactRepository = artifactRepository;
        this.missionRepository = missionRepository;
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.socialPublisherFactory = socialPublisherFactory;
        this.publicationRepository = publicationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Publication> publishArtifact(UUID artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId).orElse(null);
        if (artifact == null) {
            log.warn("Cannot publish unknown artifact {}", artifactId);
            return List.of();
        }
        PublishRequest request = buildRequest(artifact);
        List<Publication> publications = publishToChannels(artifact.getMissionId(), artifactId, request);

        long published = publications.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        if (published > 0) {
            artifact.setPublicationStatus("PUBLISHED");
            artifact.setStatus("PUBLISHED");
            artifactRepository.save(artifact);
        } else if (!publications.isEmpty()) {
            artifact.setPublicationStatus("FAILED");
            artifactRepository.save(artifact);
        }
        return publications;
    }

    @Transactional
    public List<Publication> publishToChannels(UUID missionId, UUID artifactId, PublishRequest request) {
        List<Provider> channels = providerRepository.findByProviderType("PUBLISHING").stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> "CONNECTED".equalsIgnoreCase(p.getStatus()))
                .toList();
        if (channels.isEmpty()) {
            log.warn("No CONNECTED publishing channels for mission {}; nothing published", missionId);
            return List.of();
        }

        List<Publication> results = new ArrayList<>();
        for (Provider channel : channels) {
            String platform = resolvePlatform(channel);
            String authToken = providerKeyCipher.decrypt(channel.getEncryptedApiKey());
            String contentFormat = readContentFormat(channel);
            SocialPublisher publisher = socialPublisherFactory.getPublisherForChannel(
                    platform, channel.getApiEndpoint(), authToken, contentFormat);

            PublishResponse response;
            try {
                response = publisher.publish(request);
            } catch (Exception e) {
                log.error("Publish failed for channel {} ({})", channel.getName(), platform, e);
                response = PublishResponse.error("Publish to " + platform + " failed: " + e.getMessage());
            }

            Publication publication = new Publication();
            publication.setArtifactId(artifactId);
            publication.setDestination(platform);
            publication.setStatus(response.isSuccess() ? "COMPLETED" : "FAILED");
            publication.setUrl(response.getPlatformUrl());
            publication.setPublishedAt(response.isSuccess() ? LocalDateTime.now() : null);
            publication.setErrorMessage(response.getErrorMessage());
            publication.setMetadata(metadataJson(channel.getName(), platform, request.getTitle()));
            results.add(publicationRepository.save(publication));
            log.info("Published artifact {} to {} via {}: {}",
                    artifactId, platform, channel.getName(), response.isSuccess() ? response.getPlatformUrl() : response.getErrorMessage());
        }
        return results;
    }

    private PublishRequest buildRequest(Artifact artifact) {
        Mission mission = artifact.getMissionId() != null
                ? missionRepository.findById(artifact.getMissionId()).orElse(null) : null;
        PublishRequest request = new PublishRequest();
        String missionName = mission != null && mission.getName() != null ? mission.getName() : "Mission";
        request.setTitle(artifact.getName() != null ? artifact.getName() : "Karma OS Video " + missionName);
        String description = mission != null && mission.getDescription() != null && !mission.getDescription().isBlank()
                ? mission.getDescription() : missionName;
        request.setDescription(description);
        request.setContent(artifact.getContentText() != null ? artifact.getContentText() : description);
        if ("VIDEO".equals(artifact.getArtifactType()) || "MEDIA_ASSET".equals(artifact.getArtifactType())) {
            request.setVideoUrl(artifact.getStorageLocation());
        }
        return request;
    }

    private String resolvePlatform(Provider channel) {
        JsonNode config = readConfig(channel);
        if (config.hasNonNull("platform") && !config.get("platform").asText("").isBlank()) {
            return config.get("platform").asText().toLowerCase();
        }
        return channel.getName() != null ? channel.getName().toLowerCase() : "custom";
    }

    private String readContentFormat(Provider channel) {
        JsonNode config = readConfig(channel);
        if (config.hasNonNull("contentFormat") && !config.get("contentFormat").asText("").isBlank()) {
            return config.get("contentFormat").asText();
        }
        return "text/markdown";
    }

    private JsonNode readConfig(Provider channel) {
        if (channel.getConfiguration() == null || channel.getConfiguration().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(channel.getConfiguration());
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String metadataJson(String channelName, String platform, String title) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("channel", channelName);
            meta.put("platform", platform);
            meta.put("title", title);
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return "{}";
        }
    }
}
