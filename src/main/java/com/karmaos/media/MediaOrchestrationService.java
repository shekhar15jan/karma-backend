package com.karmaos.media;

import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.execution.ExecutionService;
import com.karmaos.media.RendererClient.RenderedVideo;
import com.karmaos.media.VideoStoryboardService.Scene;
import com.karmaos.media.VideoStoryboardService.Storyboard;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.publication.Publication;
import com.karmaos.publishing.PublishRequest;
import com.karmaos.publishing.PublishingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(MediaOrchestrationService.class);

    private static final Set<String> IMAGE_PROVIDER_TYPES = Set.of("openai", "google", "gemini", "stability");
    private static final Set<String> VOICE_PROVIDER_TYPES = Set.of("openai", "google", "gemini", "elevenlabs", "edge-tts", "edge");
    private static final Map<String, String> CAPABILITY_PROVIDER_MAP = Map.of(
        "image_generation", "openai",
        "audio_generation", "edge-tts"
    );

    private final ImageProviderFactory imageProviderFactory;
    private final VoiceProviderFactory voiceProviderFactory;
    private final PublishingService publishingService;
    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final ArtifactRepository artifactRepository;
    private final ExecutionService executionService;
    private final MissionRepository missionRepository;
    private final VideoStoryboardService storyboardService;
    private final RendererClient rendererClient;
    private final String storageLocation;
    private final String channelName;
    private final String narrationVoice;
    private final String musicFile;

    public MediaOrchestrationService(ImageProviderFactory imageProviderFactory,
                                     VoiceProviderFactory voiceProviderFactory,
                                     PublishingService publishingService,
                                     ProviderRepository providerRepository,
                                     ProviderKeyCipher providerKeyCipher,
                                     ArtifactRepository artifactRepository,
                                     ExecutionService executionService,
                                     MissionRepository missionRepository,
                                     VideoStoryboardService storyboardService,
                                     RendererClient rendererClient,
                                     @Value("${app.storage.location:./data/artifacts}") String storageLocation,
                                     @Value("${app.video.channel-name:Karma OS}") String channelName,
                                     @Value("${app.video.voice:en-US-AndrewNeural}") String narrationVoice,
                                     @Value("${app.video.music-file:}") String musicFile) {
        this.imageProviderFactory = imageProviderFactory;
        this.voiceProviderFactory = voiceProviderFactory;
        this.publishingService = publishingService;
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.artifactRepository = artifactRepository;
        this.executionService = executionService;
        this.missionRepository = missionRepository;
        this.storyboardService = storyboardService;
        this.rendererClient = rendererClient;
        this.storageLocation = storageLocation;
        this.channelName = channelName;
        this.narrationVoice = narrationVoice;
        this.musicFile = musicFile;
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
            if (!response.isSuccess() && !(imageProvider instanceof LocalImageProvider)) {
                log.warn("Image provider {} failed ({}); falling back to local image generation",
                        imageProvider.getName(), response.getErrorMessage());
                imageProvider = imageProviderFactory.getFallbackProvider();
                request.setModel(imageProvider.getDefaultModel());
                response = imageProvider.generate(request);
            }
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
            boolean keylessVoice = provider != null
                    && ("edge-tts".equalsIgnoreCase(provider.getProviderType()) || "edge".equalsIgnoreCase(provider.getProviderType()));
            VoiceProvider voiceProvider = (provider != null && (!apiKey.isEmpty() || keylessVoice))
                ? voiceProviderFactory.getProvider(provider.getProviderType(), apiKey)
                : voiceProviderFactory.getFallbackProvider();

            VoiceRequest request = new VoiceRequest();
            String text = contextData != null ? contextData : "Sample narration text";
            if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                text += "\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n" + reviewerFeedback;
            }
            request.setText(text);
            request.setModel(voiceProvider.getDefaultModel());
            if (voiceProvider instanceof EdgeTtsProvider) {
                request.setVoice(voiceProvider.getDefaultModel());
            }

            long start = System.currentTimeMillis();
            VoiceResponse response = voiceProvider.synthesize(request);
            if (!response.isSuccess() && !(voiceProvider instanceof EdgeTtsProvider)) {
                log.warn("Voice provider {} failed ({}); falling back to Edge TTS",
                        voiceProvider.getName(), response.getErrorMessage());
                voiceProvider = new EdgeTtsProvider();
                request.setModel(voiceProvider.getDefaultModel());
                request.setVoice(voiceProvider.getDefaultModel());
                response = voiceProvider.synthesize(request);
            }
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
            Mission mission = missionRepository.findById(missionId).orElse(null);
            long start = System.currentTimeMillis();
            Storyboard storyboard = storyboardService.build(agentId, mission, contextData, reviewerFeedback);
            List<String> narrationAudio = synthesizeNarrations(storyboard);

            RenderedVideo rendered = null;
            String renderer = "Remotion";
            if (rendererClient.isAvailable()) {
                try {
                    rendered = rendererClient.renderVideo(buildRenderPayload(storyboard, narrationAudio));
                } catch (Exception e) {
                    log.error("Renderer video failed for mission {}; falling back to FFmpeg: {}", missionId, e.getMessage());
                }
            }
            if (rendered == null) {
                renderer = "FFmpeg";
                rendered = renderWithFfmpeg(storyboard, narrationAudio);
            }
            step.setLatencyMs((int) (System.currentTimeMillis() - start));

            Path videoFile = writeMediaFile(missionId, "video", ".mp4", rendered.bytes());
            Artifact video = new Artifact();
            video.setMissionId(missionId);
            video.setName(storyboard.title());
            video.setArtifactType("VIDEO");
            video.setStatus("COMPLETED");
            video.setContentText(storyboard.title() + "\n\n" + storyboard.description());
            video.setContentType("video/mp4");
            video.setReviewStatus("PENDING");
            video.setPublicationStatus("UNPUBLISHED");
            video.setStorageLocation(videoFile.toUri().toString());
            video.setSize(rendered.bytes().length);
            artifactRepository.save(video);

            List<String> chapters = VideoStoryboardService.chapters(storyboard.scenes(), rendered.sceneStarts());
            Artifact metadata = new Artifact();
            metadata.setMissionId(missionId);
            metadata.setName("YouTube Metadata - " + storyboard.title());
            metadata.setArtifactType("YOUTUBE_METADATA");
            metadata.setStatus("COMPLETED");
            metadata.setContentText(youtubeMetadataMarkdown(storyboard, chapters));
            metadata.setContentType("text/markdown");
            metadata.setReviewStatus("PENDING");
            metadata.setPublicationStatus("UNPUBLISHED");
            artifactRepository.save(metadata);

            saveThumbnail(missionId, storyboard);

            step.setStatus("COMPLETED");
            step.setModelUsed(renderer);
            step.setOutputData("Rendered " + storyboard.scenes().size() + "-scene video (" + rendered.durationSeconds()
                    + "s) with " + renderer + ": " + storyboard.title());
        } catch (Exception e) {
            log.error("Video step failed: agentId={}", agentId, e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    /** Narrates each scene; entries are base64 audio data URIs, or null when synthesis failed. */
    private List<String> synthesizeNarrations(Storyboard storyboard) {
        Provider provider = resolveProviderForCapability(Set.of("elevenlabs", "openai", "google", "gemini"), "elevenlabs");
        String apiKey = provider != null ? getApiKey(provider) : "";
        VoiceProvider premium = provider != null && !apiKey.isEmpty()
                && Set.of("elevenlabs", "openai", "google", "gemini").contains(provider.getProviderType().toLowerCase())
                ? voiceProviderFactory.getProvider(provider.getProviderType(), apiKey)
                : null;
        VoiceProvider edge = new EdgeTtsProvider();

        List<String> audio = new ArrayList<>();
        for (Scene scene : storyboard.scenes()) {
            String uri = premium != null ? synthesize(premium, scene.narration(), null) : null;
            if (uri == null) {
                uri = synthesize(edge, scene.narration(), narrationVoice);
            }
            audio.add(uri);
        }
        long narrated = audio.stream().filter(java.util.Objects::nonNull).count();
        log.info("Narrated {}/{} scenes ({})", narrated, audio.size(), premium != null ? premium.getName() + " + Edge fallback" : "Edge TTS");
        return audio;
    }

    private String synthesize(VoiceProvider voiceProvider, String text, String voice) {
        try {
            VoiceRequest request = new VoiceRequest();
            request.setText(text);
            request.setModel(voiceProvider.getDefaultModel());
            request.setVoice(voice != null && !voice.isBlank() ? voice : voiceProvider.getDefaultModel());
            VoiceResponse response = voiceProvider.synthesize(request);
            // Some providers report success with a placeholder URL when misconfigured; only real audio counts.
            if (response.isSuccess() && response.getAudioUrl() != null && response.getAudioUrl().startsWith("data:audio")) {
                return response.getAudioUrl();
            }
            log.warn("{} narration unusable: {}", voiceProvider.getName(),
                    response.isSuccess() ? "non-audio result" : response.getErrorMessage());
        } catch (Exception e) {
            log.warn("{} narration error: {}", voiceProvider.getName(), e.getMessage());
        }
        return null;
    }

    private Map<String, Object> buildRenderPayload(Storyboard storyboard, List<String> narrationAudio) {
        List<Map<String, Object>> scenes = new ArrayList<>();
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            Scene scene = storyboard.scenes().get(i);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("visualSpec", scene.visual());
            s.put("narration", scene.narration());
            if (narrationAudio.get(i) != null) s.put("audio", narrationAudio.get(i));
            scenes.add(s);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fps", 30);
        payload.put("resolution", Map.of("width", 1920, "height", 1080));
        payload.put("transition", "fade");
        payload.put("transitionDuration", 12);
        payload.put("enableIntro", true);
        payload.put("introTitle", storyboard.title());
        if (storyboard.thumbnailSubtitle() != null && !storyboard.thumbnailSubtitle().isBlank()) {
            payload.put("introSubtitle", storyboard.thumbnailSubtitle());
        }
        payload.put("channelName", channelName);
        payload.put("endPaddingFrames", 180);
        payload.put("captions", Map.of("burnIn", true, "preset", "youtube", "fontSize", 40,
                "maxCharsPerLine", 48, "position", 0.9));
        String music = backgroundMusicDataUri();
        if (music != null) {
            payload.put("music", music);
            payload.put("musicVolume", 0.12);
        }
        payload.put("scenes", scenes);
        return payload;
    }

    private String backgroundMusicDataUri() {
        if (musicFile == null || musicFile.isBlank()) return null;
        try {
            byte[] bytes = Files.readAllBytes(Path.of(musicFile));
            return "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Background music {} unreadable: {}", musicFile, e.getMessage());
            return null;
        }
    }

    /** Fallback when karma-renderer is down: simple narrated slides via FFmpeg. */
    private RenderedVideo renderWithFfmpeg(Storyboard storyboard, List<String> narrationAudio) throws Exception {
        VideoRequest request = new VideoRequest();
        request.setTitle(storyboard.title());
        List<VideoScene> scenes = new ArrayList<>();
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            VideoScene scene = new VideoScene();
            scene.setText(storyboard.scenes().get(i).narration());
            scene.setAudioUrl(narrationAudio.get(i));
            scenes.add(scene);
        }
        request.setScenes(scenes);
        VideoResponse response = new FFmpegVideoProvider().generate(request);
        if (!response.isSuccess()) {
            throw new IllegalStateException("Renderer unavailable at " + rendererClient.baseUrl()
                    + " and FFmpeg fallback failed: " + response.getErrorMessage());
        }
        Path output = Path.of(URI.create(response.getVideoUrl()));
        try {
            return new RenderedVideo(Files.readAllBytes(output), response.getDurationSeconds(), List.of());
        } finally {
            FFmpegVideoProvider.deleteWorkDir(output.getParent());
        }
    }

    private void saveThumbnail(UUID missionId, Storyboard storyboard) {
        if (!rendererClient.isAvailable()) return;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", storyboard.thumbnailText());
            if (storyboard.thumbnailSubtitle() != null) payload.put("subtitle", storyboard.thumbnailSubtitle());
            payload.put("badges", storyboard.badges());
            payload.put("channelName", channelName);
            byte[] png = rendererClient.renderThumbnail(payload);
            Path file = writeMediaFile(missionId, "thumbnail", ".png", png);

            Artifact thumbnail = new Artifact();
            thumbnail.setMissionId(missionId);
            thumbnail.setName("Thumbnail - " + storyboard.title());
            thumbnail.setArtifactType("THUMBNAIL");
            thumbnail.setStatus("COMPLETED");
            thumbnail.setContentText(storyboard.thumbnailText());
            thumbnail.setContentType("image/png");
            thumbnail.setReviewStatus("PENDING");
            thumbnail.setPublicationStatus("UNPUBLISHED");
            thumbnail.setStorageLocation(file.toUri().toString());
            thumbnail.setSize(png.length);
            artifactRepository.save(thumbnail);
        } catch (Exception e) {
            log.warn("Thumbnail render failed for mission {}: {}", missionId, e.getMessage());
        }
    }

    private Path writeMediaFile(UUID missionId, String kind, String extension, byte[] bytes) throws IOException {
        Path dir = Path.of(storageLocation).toAbsolutePath().normalize().resolve("media");
        Files.createDirectories(dir);
        Path file = dir.resolve(missionId + "-" + kind + "-" + System.currentTimeMillis() + extension);
        Files.write(file, bytes);
        return file;
    }

    /** Extracts the paste-ready description block (description, chapters, hashtags) from the metadata markdown. */
    private static String youtubeDescription(String markdown) {
        if (markdown == null) return null;
        int start = markdown.indexOf("## Description");
        if (start < 0) return null;
        int end = markdown.indexOf("## Tags", start);
        return markdown.substring(start + "## Description".length(), end < 0 ? markdown.length() : end).trim();
    }

    private static String youtubeMetadataMarkdown(Storyboard storyboard, List<String> chapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(storyboard.title()).append("\n\n");
        sb.append("## Description\n\n");
        sb.append(storyboard.description() == null ? "" : storyboard.description().trim()).append("\n\n");
        if (!chapters.isEmpty()) {
            sb.append("Chapters:\n");
            chapters.forEach(line -> sb.append(line).append("\n"));
            sb.append("\n");
        }
        if (!storyboard.tags().isEmpty()) {
            sb.append(storyboard.tags().stream().limit(3).map(t -> "#" + t.replaceAll("[^A-Za-z0-9]", ""))
                    .collect(java.util.stream.Collectors.joining(" "))).append("\n\n");
            sb.append("## Tags\n\n").append(String.join(", ", storyboard.tags())).append("\n");
        }
        return sb.toString();
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

            Artifact metadata = artifacts.stream()
                    .filter(a -> "YOUTUBE_METADATA".equals(a.getArtifactType()))
                    .findFirst()
                    .orElse(null);

            PublishRequest request = new PublishRequest();
            String missionName = "Mission " + missionId.toString().substring(0, 8);
            request.setTitle(target != null && target.getName() != null ? target.getName() : "Karma OS Video " + missionName);
            String description = metadata != null ? youtubeDescription(metadata.getContentText()) : null;
            request.setDescription(description != null ? description : (contextData != null ? contextData : "Generated by Karma OS"));
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
            } else if (publications.isEmpty()) {
                // Nothing configured to publish to: the video is ready for manual upload, which is not a failure.
                log.info("No connected publishing channels for mission {}; video left for manual upload", missionId);
                step.setStatus("COMPLETED");
                step.setOutputData("No publishing channels connected; download the video, thumbnail and YouTube metadata from Artifacts to upload manually.");
            } else {
                String reason = publications.stream()
                        .map(p -> p.getDestination() + ": " + p.getErrorMessage())
                        .collect(java.util.stream.Collectors.joining("; "));
                log.warn("Publishing failed on every channel for mission {}: {}", missionId, reason);
                step.setStatus("FAILED");
                step.setErrorMessage("Publishing failed on every channel: " + reason);
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
