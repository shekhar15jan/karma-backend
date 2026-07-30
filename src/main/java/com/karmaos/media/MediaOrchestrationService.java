package com.karmaos.media;

import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.execution.ExecutionService;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.publication.Publication;
import com.karmaos.publication.PublicationRepository;
import com.karmaos.publishing.PublishRequest;
import com.karmaos.publishing.PublishResponse;
import com.karmaos.publishing.SocialPublisher;
import com.karmaos.publishing.SocialPublisherFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(MediaOrchestrationService.class);

    private static final Set<String> IMAGE_PROVIDER_TYPES = Set.of("openai", "google", "gemini", "stability");
    private static final Set<String> VOICE_PROVIDER_TYPES = Set.of("openai", "google", "gemini", "elevenlabs");
    private static final Set<String> VIDEO_PROVIDER_TYPES = Set.of("ffmpeg");
    private static final Map<String, String> CAPABILITY_PROVIDER_MAP = Map.of(
        "image_generation", "openai",
        "audio_generation", "openai"
    );

    private final ImageProviderFactory imageProviderFactory;
    private final VoiceProviderFactory voiceProviderFactory;
    private final VideoProviderFactory videoProviderFactory;
    private final SocialPublisherFactory socialPublisherFactory;
    private final ProviderRepository providerRepository;
    private final ArtifactRepository artifactRepository;
    private final PublicationRepository publicationRepository;
    private final ExecutionService executionService;

    public MediaOrchestrationService(ImageProviderFactory imageProviderFactory,
                                     VoiceProviderFactory voiceProviderFactory,
                                     VideoProviderFactory videoProviderFactory,
                                     SocialPublisherFactory socialPublisherFactory,
                                     ProviderRepository providerRepository,
                                     ArtifactRepository artifactRepository,
                                     PublicationRepository publicationRepository,
                                     ExecutionService executionService) {
        this.imageProviderFactory = imageProviderFactory;
        this.voiceProviderFactory = voiceProviderFactory;
        this.videoProviderFactory = videoProviderFactory;
        this.socialPublisherFactory = socialPublisherFactory;
        this.providerRepository = providerRepository;
        this.artifactRepository = artifactRepository;
        this.publicationRepository = publicationRepository;
        this.executionService = executionService;
    }

    public ExecutionStep executeImageStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "IMAGE_GENERATION", contextData, missionId);

        try {
            Provider provider = resolveProviderForCapability(IMAGE_PROVIDER_TYPES, "openai");
            ImageProvider imageProvider = provider != null
                ? imageProviderFactory.getProvider(provider.getProviderType(), getApiKey(provider))
                : imageProviderFactory.getFallbackProvider();

            ImageRequest request = new ImageRequest();
            request.setPrompt(contextData != null ? contextData : "Generate an image based on the mission context");
            request.setModel(imageProvider.getDefaultModel());

            long start = System.currentTimeMillis();
            ImageResponse response = imageProvider.generate(request);
            step.setLatencyMs((int) (System.currentTimeMillis() - start));

            if (response.isSuccess()) {
                step.setStatus("COMPLETED");
                step.setOutputData(response.getImageUrls() != null ? String.join("\n", response.getImageUrls()) : "");

                Artifact artifact = new Artifact();
                artifact.setMissionId(missionId);
                artifact.setName("Generated Image");
                artifact.setArtifactType("MEDIA_ASSET");
                artifact.setStatus("COMPLETED");
                artifact.setContentText(response.getImageUrls() != null ? String.join("\n", response.getImageUrls()) : "");
                artifact.setContentType("image/png");
                artifact.setReviewStatus("PENDING");
                artifact.setPublicationStatus("UNPUBLISHED");
                artifact.setStorageLocation(response.getImageUrls() != null && !response.getImageUrls().isEmpty()
                    ? response.getImageUrls().get(0) : null);
                artifactRepository.save(artifact);
            } else {
                step.setStatus("FAILED");
                step.setErrorMessage(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Image step failed: agentId={}", agentId, e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    public ExecutionStep executeVoiceStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "TTS", contextData, missionId);

        try {
            Provider provider = resolveProviderForCapability(VOICE_PROVIDER_TYPES, "openai");
            VoiceProvider voiceProvider = provider != null
                ? voiceProviderFactory.getProvider(provider.getProviderType(), getApiKey(provider))
                : voiceProviderFactory.getFallbackProvider();

            VoiceRequest request = new VoiceRequest();
            request.setText(contextData != null ? contextData : "Sample narration text");
            request.setModel(voiceProvider.getDefaultModel());

            long start = System.currentTimeMillis();
            VoiceResponse response = voiceProvider.synthesize(request);
            step.setLatencyMs((int) (System.currentTimeMillis() - start));

            if (response.isSuccess()) {
                step.setStatus("COMPLETED");
                step.setOutputData(response.getAudioUrl());

                Artifact artifact = new Artifact();
                artifact.setMissionId(missionId);
                artifact.setName("Voiceover Narration");
                artifact.setArtifactType("AUDIO");
                artifact.setStatus("COMPLETED");
                artifact.setContentText(response.getAudioUrl());
                artifact.setContentType("audio/mpeg");
                artifact.setReviewStatus("PENDING");
                artifact.setPublicationStatus("UNPUBLISHED");
                artifact.setStorageLocation(response.getAudioUrl());
                artifact.setSize(response.getDurationSeconds());
                artifactRepository.save(artifact);
            } else {
                step.setStatus("FAILED");
                step.setErrorMessage(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Voice step failed: agentId={}", agentId, e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    public ExecutionStep executeVideoStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "VIDEO_GENERATION", contextData, missionId);

        try {
            Provider provider = resolveProviderForCapability(VIDEO_PROVIDER_TYPES, "ffmpeg");
            VideoProvider videoProvider = provider != null
                ? videoProviderFactory.getProvider(provider.getProviderType(), getApiKey(provider))
                : videoProviderFactory.getFallbackProvider();

            VideoRequest request = new VideoRequest();
            request.setTitle("Video for Mission " + missionId);
            request.setScript(contextData != null ? contextData : "Assemble video from generated assets");
            
            // Collect existing audio and image artifacts
            List<Artifact> artifacts = artifactRepository.findByMissionIdOrderByCreatedAtDesc(missionId);
            List<String> imageUrls = new java.util.ArrayList<>();
            String audioUrl = null;
            
            for (Artifact a : artifacts) {
                if ("AUDIO".equals(a.getArtifactType()) && audioUrl == null) {
                    audioUrl = a.getStorageLocation();
                } else if ("MEDIA_ASSET".equals(a.getArtifactType()) && "image/png".equals(a.getContentType())) {
                    if (a.getStorageLocation() != null) {
                        imageUrls.add(a.getStorageLocation());
                    }
                }
            }
            request.setAudioUrl(audioUrl);
            request.setImageUrls(imageUrls);

            long start = System.currentTimeMillis();
            VideoResponse response = videoProvider.generate(request);
            step.setLatencyMs((int) (System.currentTimeMillis() - start));

            if (response.isSuccess()) {
                step.setStatus("COMPLETED");
                step.setOutputData(response.getVideoUrl());

                Artifact artifact = new Artifact();
                artifact.setMissionId(missionId);
                artifact.setName("Final Assembled Video");
                artifact.setArtifactType("VIDEO");
                artifact.setStatus("COMPLETED");
                artifact.setContentText(response.getVideoUrl());
                artifact.setContentType("video/mp4");
                artifact.setReviewStatus("PENDING");
                artifact.setPublicationStatus("UNPUBLISHED");
                artifact.setStorageLocation(response.getVideoUrl());
                artifact.setSize(response.getDurationSeconds());
                artifactRepository.save(artifact);
            } else {
                step.setStatus("FAILED");
                step.setErrorMessage(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Video step failed: agentId={}", agentId, e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    public ExecutionStep executePublishStep(UUID executionId, UUID flowId, UUID agentId,
                                            int stepOrder, String contextData, UUID missionId) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "PUBLISH", contextData, missionId);

        try {
            PublishRequest request = new PublishRequest();
            request.setTitle("Karma OS Video " + missionId.toString().substring(0, 8));
            request.setDescription(contextData != null ? contextData : "Generated by Karma OS");
            request.setContent(contextData);

            List<Artifact> artifacts = artifactRepository.findByMissionIdOrderByCreatedAtDesc(missionId);
            for (Artifact a : artifacts) {
                if ("VIDEO".equals(a.getArtifactType()) || "MEDIA_ASSET".equals(a.getArtifactType())) {
                    request.setVideoUrl(a.getStorageLocation());
                }
            }

            Provider provider = resolveProviderForCapability(Set.of("google", "youtube"), "google");
            String apiKey = provider != null ? getApiKey(provider) : null;

            SocialPublisher publisher = socialPublisherFactory.getPublisher("youtube", apiKey, null);
            PublishResponse response = publisher.publish(request);

            Publication publication = new Publication();
            publication.setArtifactId(!artifacts.isEmpty() ? artifacts.get(0).getId() : null);
            publication.setDestination("youtube");
            publication.setStatus(response.isSuccess() ? "COMPLETED" : "FAILED");
            publication.setUrl(response.getPlatformUrl());
            publication.setPublishedAt(response.isSuccess() ? LocalDateTime.now() : null);
            publication.setErrorMessage(response.getErrorMessage());
            publicationRepository.save(publication);

            if (response.isSuccess()) {
                step.setStatus("COMPLETED");
                step.setOutputData("Published to YouTube: " + response.getPlatformUrl());
                for (Artifact a : artifacts) {
                    a.setPublicationStatus("PUBLISHED");
                    artifactRepository.save(a);
                }
            } else {
                step.setStatus("FAILED");
                step.setErrorMessage(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Publish step failed", e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    private Provider resolveProviderForCapability(Set<String> preferredTypes, String defaultType) {
        for (Provider p : providerRepository.findByStatus("CONNECTED")) {
            if (preferredTypes.contains(p.getProviderType().toLowerCase())) {
                return p;
            }
        }
        for (Provider p : providerRepository.findByStatus("CONNECTED")) {
            if (p.getProviderType().toLowerCase().contains(defaultType)) {
                return p;
            }
        }
        List<Provider> all = providerRepository.findByStatus("CONNECTED");
        return all.isEmpty() ? null : all.get(0);
    }

    private String getApiKey(Provider provider) {
        if (provider == null || provider.getEncryptedApiKey() == null) return "";
        return new String(provider.getEncryptedApiKey());
    }

    private ExecutionStep createStep(UUID executionId, UUID flowId, UUID agentId,
                                     int stepOrder, String stepType, String contextData, UUID missionId) {
        ExecutionStep step = new ExecutionStep();
        step.setExecutionId(executionId);
        step.setFlowId(flowId);
        step.setAgentId(agentId);
        step.setStepOrder(stepOrder);
        step.setStepType(stepType);
        step.setStatus("RUNNING");
        step.setStartedAt(LocalDateTime.now());
        step.setInputData(contextData);
        return step;
    }
}
