package com.karmaos.media;

import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.execution.ExecutionService;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.publication.Publication;
import com.karmaos.publishing.PublishRequest;
import com.karmaos.publishing.PublishingService;
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
    private static final Set<String> VOICE_PROVIDER_TYPES = Set.of("openai", "google", "gemini", "elevenlabs", "edge-tts", "edge");
    private static final Set<String> VIDEO_PROVIDER_TYPES = Set.of("ffmpeg");
    private static final Map<String, String> CAPABILITY_PROVIDER_MAP = Map.of(
        "image_generation", "openai",
        "audio_generation", "edge-tts"
    );

    private final ImageProviderFactory imageProviderFactory;
    private final VoiceProviderFactory voiceProviderFactory;
    private final VideoProviderFactory videoProviderFactory;
    private final PublishingService publishingService;
    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final ArtifactRepository artifactRepository;
    private final ExecutionService executionService;

    public MediaOrchestrationService(ImageProviderFactory imageProviderFactory,
                                     VoiceProviderFactory voiceProviderFactory,
                                     VideoProviderFactory videoProviderFactory,
                                     PublishingService publishingService,
                                     ProviderRepository providerRepository,
                                     ProviderKeyCipher providerKeyCipher,
                                     ArtifactRepository artifactRepository,
                                     ExecutionService executionService) {
        this.imageProviderFactory = imageProviderFactory;
        this.voiceProviderFactory = voiceProviderFactory;
        this.videoProviderFactory = videoProviderFactory;
        this.publishingService = publishingService;
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.artifactRepository = artifactRepository;
        this.executionService = executionService;
    }

    public ExecutionStep executeImageStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "IMAGE_GENERATION", contextData, missionId);

        try {
            Provider provider = resolveProviderForCapability(IMAGE_PROVIDER_TYPES, "openai");
            String apiKey = provider != null ? getApiKey(provider) : "";
            ImageProvider imageProvider = (provider != null && !apiKey.isEmpty())
                ? imageProviderFactory.getProvider(provider.getProviderType(), apiKey)
                : imageProviderFactory.getFallbackProvider();

            ImageRequest request = new ImageRequest();
            String prompt = contextData != null ? contextData : "Generate an image based on the mission context";
            if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                prompt += "\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n" + reviewerFeedback;
            }
            request.setPrompt(prompt);
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
                                          int stepOrder, String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "TTS", contextData, missionId);

        try {
            Provider provider = resolveProviderForCapability(VOICE_PROVIDER_TYPES, "openai");
            String apiKey = provider != null ? getApiKey(provider) : "";
            VoiceProvider voiceProvider = (provider != null && !apiKey.isEmpty())
                ? voiceProviderFactory.getProvider(provider.getProviderType(), apiKey)
                : voiceProviderFactory.getFallbackProvider();

            VoiceRequest request = new VoiceRequest();
            String text = contextData != null ? contextData : "Sample narration text";
            if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                text += "\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n" + reviewerFeedback;
            }
            request.setText(text);
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
                                          int stepOrder, String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "VIDEO_GENERATION", contextData, missionId);

        try {
            Provider provider = resolveProviderForCapability(VIDEO_PROVIDER_TYPES, "ffmpeg");
            VideoProvider videoProvider = provider != null
                ? videoProviderFactory.getProvider(provider.getProviderType(), getApiKey(provider))
                : videoProviderFactory.getFallbackProvider();

            VideoRequest request = new VideoRequest();
            request.setTitle("Video for Mission " + missionId);
            String script = contextData != null ? contextData : "Assemble video from generated assets";
            if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                script += "\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n" + reviewerFeedback;
            }
            request.setScript(script);
            
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
            if (imageUrls.isEmpty()) {
                ImageProvider imageProvider = imageProviderFactory.getFallbackProvider();
                ImageRequest ir = new ImageRequest();
                ir.setPrompt(contextData != null ? contextData : "Generated video background for mission " + missionId);
                ir.setSize("1280x720");
                ir.setN(3);
                ImageResponse imgResp = imageProvider.generate(ir);
                if (imgResp.isSuccess() && imgResp.getImageUrls() != null) {
                    imageUrls.addAll(imgResp.getImageUrls());
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
            List<Artifact> artifacts = artifactRepository.findByMissionIdOrderByCreatedAtDesc(missionId);
            Artifact target = artifacts.stream()
                    .filter(a -> "VIDEO".equals(a.getArtifactType()) || "MEDIA_ASSET".equals(a.getArtifactType()))
                    .findFirst()
                    .orElse(artifacts.isEmpty() ? null : artifacts.get(0));

            PublishRequest request = new PublishRequest();
            String missionName = "Mission " + missionId.toString().substring(0, 8);
            request.setTitle(target != null && target.getName() != null ? target.getName() : "Karma OS Video " + missionName);
            request.setDescription(contextData != null ? contextData : "Generated by Karma OS");
            request.setContent(contextData);
            if (target != null && target.getStorageLocation() != null) {
                request.setVideoUrl(target.getStorageLocation());
            }

            List<Publication> publications = publishingService.publishToChannels(missionId, target != null ? target.getId() : null, request);
            List<Publication> completed = publications.stream()
                    .filter(p -> "COMPLETED".equals(p.getStatus()))
                    .toList();

            if (!completed.isEmpty()) {
                step.setStatus("COMPLETED");
                step.setOutputData("Published to " + completed.size() + " channel(s): "
                        + completed.stream().map(Publication::getDestination).collect(java.util.stream.Collectors.joining(", ")));
                for (Artifact a : artifacts) {
                    a.setPublicationStatus("PUBLISHED");
                    artifactRepository.save(a);
                }
            } else {
                String reason = publications.isEmpty()
                        ? "No CONNECTED publishing channels"
                        : publications.stream()
                                .map(p -> p.getDestination() + ": " + p.getErrorMessage())
                                .collect(java.util.stream.Collectors.joining("; "));
                step.setStatus("FAILED");
                step.setErrorMessage(reason);
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
        List<Provider> candidates = providerRepository.findByStatus("CONNECTED").stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                .toList();
        for (Provider p : candidates) {
            if (p.getProviderType() != null && preferredTypes.contains(p.getProviderType().toLowerCase())) {
                return p;
            }
        }
        for (Provider p : candidates) {
            if (p.getProviderType() != null && p.getProviderType().toLowerCase().contains(defaultType)) {
                return p;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private String getApiKey(Provider provider) {
        if (provider == null) return "";
        return providerKeyCipher.decrypt(provider.getEncryptedApiKey());
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
