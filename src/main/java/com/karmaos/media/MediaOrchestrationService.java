package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karmaos.agent.Agent;
import com.karmaos.agent.AgentRepository;
import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.artifact.ArtifactStorageService;
import com.karmaos.execution.Execution;
import com.karmaos.execution.ExecutionRepository;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.execution.ExecutionService;
import com.karmaos.execution.ExecutionStepRepository;
import com.karmaos.junction.AgentSkillRepository;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.publication.Publication;
import com.karmaos.publishing.PublishRequest;
import com.karmaos.publishing.PublishingService;
import com.karmaos.settings.RuntimeSettings;
import com.karmaos.visual.VisualDirectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(MediaOrchestrationService.class);

    private static final Set<String> IMAGE_PROVIDER_TYPES = Set.of("google", "gemini", "cloudflare");
    private static final Set<String> VOICE_PROVIDER_TYPES = Set.of("google", "gemini", "edge-tts", "edge");
    private static final Set<String> VIDEO_PROVIDER_TYPES = Set.of("remotion");
    private static final Set<String> MUSIC_PROVIDER_TYPES = Set.of("google-music");
    private static final Map<String, String> CAPABILITY_PROVIDER_MAP = Map.of(
        "image_generation", "google",
        "audio_generation", "edge-tts",
        "music_generation", "google-music"
    );
    private static final List<String> ROTATION_THEMES = List.of(
        "whiteboard", "corporate", "minimal", "dark", "glass", "technical", "codeatcloud");
    private static final List<String> ROTATION_TRANSITIONS = List.of(
        "fade", "slide-left", "slide-right", "wipe-left", "wipe-right", "zoom");
    private static final List<String> BACKGROUND_PATTERNS = List.of("grid", "dots", "plain");

    private final ImageProviderFactory imageProviderFactory;
    private final VoiceProviderFactory voiceProviderFactory;
    private final VideoProviderFactory videoProviderFactory;
    private final MusicProviderFactory musicProviderFactory;
    private final PublishingService publishingService;
    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final AgentRepository agentRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactStorageService artifactStorageService;
    private final ExecutionService executionService;
    private final RuntimeSettings runtimeSettings;
    private final ObjectMapper objectMapper;
    private final MissionRepository missionRepository;
    private final ExecutionStepRepository executionStepRepository;
    private final ExecutionRepository executionRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final VisualDirectorService visualDirectorService;

    public MediaOrchestrationService(ImageProviderFactory imageProviderFactory,
                                      VoiceProviderFactory voiceProviderFactory,
                                      VideoProviderFactory videoProviderFactory,
                                      MusicProviderFactory musicProviderFactory,
                                      PublishingService publishingService,
                                      ProviderRepository providerRepository,
                                      ProviderKeyCipher providerKeyCipher,
                                      AgentRepository agentRepository,
                                      ArtifactRepository artifactRepository,
                                      ArtifactStorageService artifactStorageService,
                                      ExecutionService executionService,
                                      RuntimeSettings runtimeSettings,
                                      ObjectMapper objectMapper,
                                      MissionRepository missionRepository,
                                      ExecutionStepRepository executionStepRepository,
                                      ExecutionRepository executionRepository,
                                      AgentSkillRepository agentSkillRepository,
                                      VisualDirectorService visualDirectorService) {
        this.imageProviderFactory = imageProviderFactory;
        this.voiceProviderFactory = voiceProviderFactory;
        this.videoProviderFactory = videoProviderFactory;
        this.musicProviderFactory = musicProviderFactory;
        this.publishingService = publishingService;
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.agentRepository = agentRepository;
        this.artifactRepository = artifactRepository;
        this.artifactStorageService = artifactStorageService;
        this.executionService = executionService;
        this.runtimeSettings = runtimeSettings;
        this.objectMapper = objectMapper;
        this.missionRepository = missionRepository;
        this.executionStepRepository = executionStepRepository;
        this.executionRepository = executionRepository;
        this.agentSkillRepository = agentSkillRepository;
        this.visualDirectorService = visualDirectorService;
    }

    public ExecutionStep executeImageStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "IMAGE_GENERATION", contextData, missionId);

        try {
            requireAgentSkill(agentId);

            Provider primaryProvider = resolveMissionPreferredImageProvider(missionId);
            if (primaryProvider == null) {
                primaryProvider = resolveGeminiImageProvider(agentId);
            }
            if (primaryProvider == null) {
                primaryProvider = resolveAgentPreferredProvider(agentId, IMAGE_PROVIDER_TYPES);
                if (primaryProvider == null) {
                    primaryProvider = resolveProviderForCapability(IMAGE_PROVIDER_TYPES, "cloudflare");
                }
            }
            if (primaryProvider == null) {
                log.error("Image step failed: no image-capable provider is configured");
                step.setStatus("FAILED");
                step.setErrorMessage("No image generation provider is configured");
                step.setCompletedAt(LocalDateTime.now());
                return executionService.saveStep(executionId, step);
            }
            String apiKey = getApiKey(primaryProvider);
            if (apiKey.isEmpty()) {
                log.error("Image step failed: provider {} has no API key configured", primaryProvider.getProviderType());
                step.setStatus("FAILED");
                step.setErrorMessage("No image generation provider with a valid API key is configured");
                step.setCompletedAt(LocalDateTime.now());
                return executionService.saveStep(executionId, step);
            }
            ImageProvider primaryImageProvider = imageProviderFactory.getProvider(primaryProvider.getProviderType(), apiKey, primaryProvider.getApiEndpoint());
            List<String> configuredModels = parseProviderModels(primaryProvider);
            if (primaryImageProvider instanceof GoogleImageProvider gip && !configuredModels.isEmpty()) {
                gip.setConfiguredModels(configuredModels);
            }
            ImageProvider fallbackProvider = imageProviderFactory.getFallbackProvider();

            List<String> imagePrompts = buildScenePrompts(contextData);
            String characterSheet = buildCharacterSheet(contextData);
            long missionSeed = deterministicSeed(missionId);
            if (characterSheet != null && !characterSheet.isBlank()) {
                for (int ai = 0; ai < imagePrompts.size(); ai++) {
                    imagePrompts.set(ai, characterSheet + "\n" + imagePrompts.get(ai));
                }
            }
            if (reviewerFeedback != null && !reviewerFeedback.isBlank() && imagePrompts.size() == 1 && !(imagePrompts.get(0).equals(contextData))) {
                String corrected = imagePrompts.get(0);
                imagePrompts.set(0, corrected + "\n\nREVIEWER FEEDBACK:\n" + reviewerFeedback);
            } else if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                imagePrompts.set(0, imagePrompts.get(0) + "\n\nREVIEWER FEEDBACK:\n" + reviewerFeedback);
            }

            long totalStart = System.currentTimeMillis();
            List<String> allUrls = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < imagePrompts.size(); i++) {
                String safePrompt = ContentSafetyFilter.sanitizeImagePrompt(imagePrompts.get(i));
                String sceneUrl = tryGenerateScene(primaryImageProvider, fallbackProvider, safePrompt, i, missionSeed);
                if (sceneUrl != null) {
                    allUrls.add(sceneUrl);
                    persistImageArtifact(missionId, sceneUrl, i, artifactReviewStatus(executionId));
                } else {
                    failures.add("scene " + i + ": all providers failed");
                }
            }
            step.setLatencyMs((int) (System.currentTimeMillis() - totalStart));

            if (!allUrls.isEmpty()) {
                step.setStatus("COMPLETED");
                step.setOutputData(String.join("\n", allUrls));
            } else {
                step.setStatus("FAILED");
                step.setErrorMessage("Image generation failed for all scenes: " + String.join(" | ", failures));
            }
        } catch (Exception e) {
            log.error("Image step failed: agentId={}", agentId, e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    private String tryGenerateScene(ImageProvider primary, ImageProvider fallback, String prompt, int sceneIndex, Long seed) {
        ImageRequest request = new ImageRequest();
        request.setPrompt(prompt);
        request.setModel(primary.getDefaultModel());
        request.setN(1);
        request.setSeed(seed);
        ImageResponse response = primary.generate(request);
        if (response.isSuccess() && response.getImageUrls() != null && !response.getImageUrls().isEmpty()) {
            return response.getImageUrls().get(0);
        }
        log.warn("Primary image provider failed for scene {}: {}, trying fallback", sceneIndex, response.getErrorMessage());
        if (fallback != null) {
            request.setModel(fallback.getDefaultModel());
            request.setSeed(seed);
            response = fallback.generate(request);
            if (response.isSuccess() && response.getImageUrls() != null && !response.getImageUrls().isEmpty()) {
                return response.getImageUrls().get(0);
            }
            log.warn("Fallback image provider also failed for scene {}: {}", sceneIndex, response.getErrorMessage());
        }
        return null;
    }

    private List<String> parseProviderModels(Provider provider) {
        if (provider.getModels() == null || provider.getModels().isBlank()) return List.of();
        try {
            var nodes = objectMapper.readTree(provider.getModels());
            if (nodes.isArray()) {
                List<String> result = new ArrayList<>();
                for (var n : nodes) {
                    String v = n.asText();
                    if (v != null && !v.isBlank()) result.add(v);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to parse provider models JSON: {}", e.getMessage());
        }
        return List.of();
    }

    private boolean isImageContentType(String contentType) {
        if (contentType == null) return false;
        String t = contentType.toLowerCase();
        return t.startsWith("image/");
    }

    private String resolveImageContentType(String url) {
        if (url == null) return "image/png";
        if (url.startsWith("image/jpeg") || url.startsWith("image/webp")) {
            return url.split(";")[0];
        }
        int bodyStart = url.indexOf(',');
        if (bodyStart >= 0) {
            String base64 = url.substring(bodyStart + 1);
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(base64);
                if (bytes.length >= 3 && bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8) {
                    return "image/jpeg";
                }
                if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                        && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
                    return "image/webp";
                }
            } catch (IllegalArgumentException ignored) { }
        }
        return "image/png";
    }

    private String artifactReviewStatus(UUID executionId) {
        return executionRepository.findById(executionId)
                .map(e -> "AUTO".equalsIgnoreCase(e.getMode()) ? "APPROVED" : "PENDING")
                .orElse("PENDING");
    }

    private void persistImageArtifact(UUID missionId, String url, int sceneIndex, String reviewStatus) {
        Artifact artifact = new Artifact();
        artifact.setMissionId(missionId);
        artifact.setName("Generated Scene Image " + (sceneIndex + 1));
        artifact.setArtifactType("MEDIA_ASSET");
        artifact.setStatus("COMPLETED");
        artifact.setContentText(url);
        artifact.setContentType(resolveImageContentType(url));
        artifact.setReviewStatus(reviewStatus);
        artifact.setPublicationStatus("UNPUBLISHED");
        artifact.setStorageLocation(url);
        artifact.setMetadata("{\"sceneIndex\":" + sceneIndex + "}");
        artifactStorageService.persist(artifact, missionId);
        artifactRepository.save(artifact);
    }

    private List<String> buildScenePrompts(String contextData) {
        if (contextData == null || contextData.isBlank()) {
            return List.of("Generate an image based on the mission context");
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = extractJsonNode(contextData);
            if (root != null) {
                com.fasterxml.jackson.databind.JsonNode sections = null;
                if (root.has("scenes") && root.get("scenes").isArray()) {
                    sections = root.get("scenes");
                } else if (root.has("script_body") && root.get("script_body").isArray()) {
                    sections = root.get("script_body");
                }
                if (sections != null && sections.size() > 0) {
                    List<String> prompts = new java.util.ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode section : sections) {
                        String p = null;
                        for (String key : List.of("image_prompt", "image_prompts", "visual_description",
                                "visual_description_text", "speaker_text", "narration_text", "description")) {
                            if (section.hasNonNull(key)) {
                                p = section.get(key).asText();
                                break;
                            }
                        }
                        if (p == null || p.isBlank()) {
                            p = section.toString();
                        }
                        prompts.add(p.trim());
                    }
                    return prompts;
                }
            }
        } catch (Exception ignored) {
            // not JSON — treat as single prompt below
        }
        List<String> markdownScenes = splitMarkdownScenes(contextData);
        if (!markdownScenes.isEmpty()) {
            return markdownScenes;
        }
        return List.of(contextData);
    }

    private List<String> splitMarkdownScenes(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> scenes = new java.util.ArrayList<>();
        String[] lines = text.split("\\R");
        StringBuilder current = new StringBuilder();
        java.util.regex.Pattern heading = java.util.regex.Pattern.compile(
                "(?i)^\\s*(#{1,6}\\s*)?(scene\\s*\\d+|scene\\s*[:#.-]+|\\d+\\.\\s*scene)\\b.*$");
        int matched = 0;
        for (String line : lines) {
            if (heading.matcher(line).matches()) {
                if (current.length() > 0) {
                    scenes.add(current.toString().trim());
                }
                current = new StringBuilder();
                matched++;
            }
            current.append(line).append("\n");
        }
        if (current.length() > 0) {
            scenes.add(current.toString().trim());
        }
        return matched >= 2 ? scenes : List.of();
    }

    private long deterministicSeed(UUID id) {
        if (id == null) return 12345L;
        return (id.getMostSignificantBits() ^ id.getLeastSignificantBits()) & 0x7FFFFFFFL;
    }

    private String buildCharacterSheet(String contextData) {
        if (contextData == null || contextData.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode root = extractJsonNode(contextData);
            if (root == null) return null;
            if (!root.has("characters") || !root.get("characters").isArray()) return null;
            java.util.Map<String, String> characters = new java.util.LinkedHashMap<>();
            for (com.fasterxml.jackson.databind.JsonNode c : root.get("characters")) {
                String name = c.hasNonNull("name") ? c.get("name").asText() : null;
                String desc = null;
                for (String dk : List.of("visual_style_notes", "visual_style", "appearance", "description")) {
                    if (c.hasNonNull(dk)) { desc = c.get(dk).asText(); break; }
                }
                if (name != null && !name.isBlank()) {
                    characters.put(name, desc != null && !desc.isBlank() ? desc : null);
                }
            }

            if (characters.isEmpty()) return null;
            StringBuilder sheet = new StringBuilder("CHARACTER CONSISTENCY: keep these characters visually identical in appearance, colours and outfits across all panels:");
            for (Map.Entry<String, String> e : characters.entrySet()) {
                if (sheet.length() > 900) break;
                sheet.append("\n- ").append(e.getKey());
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    String style = e.getValue().length() > 90 ? e.getValue().substring(0, 90) : e.getValue();
                    sheet.append(": ").append(style);
                }
            }
            return sheet.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public ExecutionStep executeVoiceStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "TTS", contextData, missionId);

        try {
            requireAgentSkill(agentId);
            Provider provider = resolveAgentPreferredProvider(agentId, VOICE_PROVIDER_TYPES);
            if (provider == null) {
                provider = resolveProviderForCapability(VOICE_PROVIDER_TYPES, "edge-tts");
            }
            String apiKey = provider != null ? getApiKey(provider) : "";
            boolean keylessVoice = provider != null
                    && ("edge-tts".equalsIgnoreCase(provider.getProviderType()) || "edge".equalsIgnoreCase(provider.getProviderType()));
            VoiceProvider voiceProvider = (provider != null && (!apiKey.isEmpty() || keylessVoice))
                ? voiceProviderFactory.getProvider(provider.getProviderType(), apiKey)
                : voiceProviderFactory.getFallbackProvider();

            String text = extractNarrationText(contextData);
            if (text == null || text.isBlank()) {
                text = contextData != null ? contextData : "Sample narration text";
            }
            List<String> speakerTexts = extractSpeakerTexts(contextData);
            if (speakerTexts.isEmpty()) {
                speakerTexts = List.of(text);
            }
            if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                for (int i = 0; i < speakerTexts.size(); i++) {
                    speakerTexts.set(i, speakerTexts.get(i) + "\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n" + reviewerFeedback);
                }
            }

            long start = System.currentTimeMillis();
            int synthesized = 0;
            List<String> sceneAudioUrls = new ArrayList<>();
            // One MP3 per scene, persisted with sceneIndex metadata for frame-accurate sync.
            for (int i = 0; i < speakerTexts.size(); i++) {
                VoiceRequest sceneRequest = new VoiceRequest();
                sceneRequest.setText(speakerTexts.get(i));
                sceneRequest.setModel(voiceProvider.getDefaultModel());
                if (voiceProvider instanceof EdgeTtsProvider) {
                    sceneRequest.setVoice(voiceProvider.getDefaultModel());
                }
                VoiceResponse sceneResponse = voiceProvider.synthesize(sceneRequest);
                if (sceneResponse.isSuccess()) {
                    sceneAudioUrls.add(sceneResponse.getAudioUrl());
                    persistSceneAudioArtifact(missionId, "Scene Narration " + (i + 1), sceneResponse.getAudioUrl(),
                            sceneResponse.getDurationSeconds(), i, artifactReviewStatus(executionId));
                    synthesized++;
                } else {
                    log.warn("Per-scene narration {} failed: {}", i, sceneResponse.getErrorMessage());
                    sceneAudioUrls.add(null);
                }
            }

            // Combined narration artifact retained for ffmpeg-provider back-compat.
            VoiceRequest request = new VoiceRequest();
            request.setText(text);
            request.setModel(voiceProvider.getDefaultModel());
            if (voiceProvider instanceof EdgeTtsProvider) {
                request.setVoice(voiceProvider.getDefaultModel());
            }
            Double targetSpeed = computeTargetSpeed(missionId, text);
            if (targetSpeed != null) {
                request.setSpeed(targetSpeed);
                log.info("Narration speed adjusted to {}x to fill target duration for mission {}", targetSpeed, missionId);
            }
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
                artifact.setReviewStatus(artifactReviewStatus(executionId));
                artifact.setPublicationStatus("UNPUBLISHED");
                artifact.setStorageLocation(response.getAudioUrl());
                artifact.setSize(response.getDurationSeconds());
                artifactStorageService.persist(artifact, missionId);
                artifactRepository.save(artifact);

                if (synthesized > 0) {
                    log.info("Voice step synthesized {} per-scene narration(s) for mission {}", synthesized, missionId);
                }
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

    private void persistSceneAudioArtifact(UUID missionId, String name, String audioUrl, long sizeSeconds, int sceneIndex, String reviewStatus) {
        Artifact artifact = new Artifact();
        artifact.setMissionId(missionId);
        artifact.setName(name);
        artifact.setArtifactType("AUDIO");
        artifact.setStatus("COMPLETED");
        artifact.setContentText(audioUrl);
        artifact.setContentType("audio/mpeg");
        artifact.setReviewStatus(reviewStatus);
        artifact.setPublicationStatus("UNPUBLISHED");
        artifact.setStorageLocation(audioUrl);
        artifact.setSize(sizeSeconds);
        artifact.setMetadata("{\"sceneIndex\":" + sceneIndex + "}");
        artifactStorageService.persist(artifact, missionId);
        artifactRepository.save(artifact);
    }

    public ExecutionStep executeMusicStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "MUSIC_GENERATION", contextData, missionId);

        try {
            requireAgentSkill(agentId);
            Provider provider = resolveAgentPreferredProvider(agentId, MUSIC_PROVIDER_TYPES);
            if (provider == null) {
                provider = resolveProviderForCapability(MUSIC_PROVIDER_TYPES, "google-music");
            }
            String apiKey = provider != null ? getApiKey(provider) : "";
            MusicProvider musicProvider = (provider != null && !apiKey.isEmpty())
                ? musicProviderFactory.getProvider(provider.getProviderType(), apiKey)
                : musicProviderFactory.getFallbackProvider();

            MusicRequest request = new MusicRequest();
            String prompt = contextData != null && !contextData.isBlank()
                ? contextData : "Calm ambient background music matching the mission tone";
            if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                prompt += "\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n" + reviewerFeedback;
            }
            request.setPrompt(prompt);
            request.setModel(musicProvider.getDefaultModel());
            request.setDurationSeconds(runtimeSettings.getInt(RuntimeSettings.KEY_MUSIC_MAX_DURATION, 30));

            long start = System.currentTimeMillis();
            MusicResponse response = musicProvider.generate(request);
            step.setLatencyMs((int) (System.currentTimeMillis() - start));

            if (response.isSuccess()) {
                step.setStatus("COMPLETED");
                step.setOutputData(response.getAudioUrl());

                Artifact artifact = new Artifact();
                artifact.setMissionId(missionId);
                artifact.setName("Background Music");
                artifact.setArtifactType("AUDIO");
                artifact.setStatus("COMPLETED");
                artifact.setContentText(response.getAudioUrl());
                artifact.setContentType("audio/wav");
                artifact.setReviewStatus(artifactReviewStatus(executionId));
                artifact.setPublicationStatus("UNPUBLISHED");
                artifact.setStorageLocation(response.getAudioUrl());
                artifact.setSize(response.getDurationSeconds());
                artifactStorageService.persist(artifact, missionId);
                artifactRepository.save(artifact);
            } else {
                step.setStatus("FAILED");
                step.setErrorMessage(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Music step failed: agentId={}", agentId, e);
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
            requireAgentSkill(agentId);
            Provider provider = resolveProviderForCapability(VIDEO_PROVIDER_TYPES, "remotion");
            String providerType = provider != null && provider.getProviderType() != null
                    ? provider.getProviderType().toLowerCase() : "remotion";
            VideoProvider videoProvider = provider != null
                ? videoProviderFactory.getProvider(provider.getProviderType(), getApiKey(provider))
                : videoProviderFactory.getFallbackProvider();

            VideoRequest request = new VideoRequest();
            String missionLabel = resolveMissionName(missionId);
            request.setTitle(missionLabel);
            String script = resolveScriptForVideo(executionId, contextData);
            if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                script += "\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n" + reviewerFeedback;
            }
            request.setScript(script);

            // Collect upstream artifacts: per-scene audio (by sceneIndex), combined
            // narration, background music and scene images (legacy ffmpeg path disabled).
            List<Artifact> artifacts = artifactRepository.findByMissionIdOrderByCreatedAtDesc(missionId);
            java.util.Map<Integer, String> sceneAudio = new java.util.TreeMap<>();
            java.util.Map<Integer, String> sceneImages = new java.util.TreeMap<>();
            String audioUrl = null;
            String musicUrl = null;
            for (Artifact a : artifacts) {
                if (!"AUDIO".equals(a.getArtifactType()) && !"MEDIA_ASSET".equals(a.getArtifactType())) continue;
                String location = a.getStorageLocation();
                if (location == null) continue;
                if ("AUDIO".equals(a.getArtifactType())) {
                    if (isMusicArtifact(a)) {
                        if (musicUrl == null) musicUrl = location;
                        continue;
                    }
                    Integer sceneIndex = extractSceneIndex(a.getMetadata());
                    if (sceneIndex != null) {
                        sceneAudio.putIfAbsent(sceneIndex, location);
                    } else if (audioUrl == null) {
                        audioUrl = location;
                    }
                } else if (isImageContentType(a.getContentType())) {
                    Integer sceneIndex = extractSceneIndex(a.getMetadata());
                    if (sceneIndex != null) {
                        sceneImages.putIfAbsent(sceneIndex, location);
                    } else {
                        sceneImages.putIfAbsent(sceneImages.size(), location);
                    }
                }
            }

            // Remotion is the only supported video provider
            List<VideoScene> scenes = buildRemotionScenes(script, sceneAudio, audioUrl, sceneImages);
            // Production Wiring: V2 Visual Director — enrich with semantic intent (preserves V1)
            String renderingMode = runtimeSettings.getString(com.karmaos.visual.VisualDirectorService.KEY_VIDEO_RENDERING_MODE, "V1");
            if ("V2".equalsIgnoreCase(renderingMode)) {
                visualDirectorService.enrichVideoScenes(scenes);
                log.info("V2 rendering mode enabled — enriched {} scenes with semantic intent", scenes.size());
            }
            applyMissionTheme(scenes, missionId, executionId);
            if (scenes.isEmpty()) {
                step.setStatus("FAILED");
                step.setErrorMessage("Remotion video step found no scenes with a visual spec in the script");
                step.setCompletedAt(LocalDateTime.now());
                return executionService.saveStep(executionId, step);
            }
            // Cap scenes to 22 to match new top-tier quality settings (words_per_scene 55, target 480s, ~20-24 scenes, animated motion)
            int maxVideoScenes = runtimeSettings.getInt(RuntimeSettings.KEY_VIDEO_MAX_SCENES, 22);
            maxVideoScenes = Math.min(maxVideoScenes, 22);
            if (scenes.size() > maxVideoScenes) {
                log.warn("Truncating video scenes from {} to {} to match quality bar (words_per_scene 55)", scenes.size(), maxVideoScenes);
                scenes = new java.util.ArrayList<>(scenes.subList(0, maxVideoScenes));
            }
            
            // Extract timeline_events from context (from Editor Agent) as a fallback
            // for scene 0 only when the script did not provide per-scene events.
            if (contextData != null && contextData.contains("\"timeline_events\"")) {
                try {
                    com.fasterxml.jackson.databind.JsonNode ctxNode = extractJsonNode(contextData);
                    if (ctxNode != null && ctxNode.has("timeline_events") && !scenes.isEmpty()
                            && scenes.get(0).getTimelineEvents() == null) {
                        scenes.get(0).setTimelineEvents(ctxNode.get("timeline_events").toString());
                    }
                } catch (Exception ignored) {}
            }
            
            request.setScenes(scenes);
            request.setAudioUrl(audioUrl);
            request.setMusicUrl(musicUrl);
            request.setIntroTitle(missionLabel);
            request.setIntroSubtitle(resolveMissionSubtitle(missionId));
            // Refinement 1: disable intro for immediate hook (0.0 sec visual+audio)
            request.setEnableIntro(false);
            request.setResolution(runtimeSettings.getString(RuntimeSettings.KEY_VIDEO_RESOLUTION, "1920x1080"));
            request.setSceneMotion(runtimeSettings.getString(RuntimeSettings.KEY_VIDEO_SCENE_MOTION, "animated"));

            // Per-run visual variety: rotate default transition and background pattern.
            // Fix transitions bug: assign rotating transition directly per scene (Remotion expects scene.transition, not root sceneTransitions)
            int styleSeed = (executionId.hashCode() & Integer.MAX_VALUE);
            request.setTransition(ROTATION_TRANSITIONS.get(styleSeed % ROTATION_TRANSITIONS.size()));
            request.setBackgroundPattern(BACKGROUND_PATTERNS.get(styleSeed % BACKGROUND_PATTERNS.size()));
            for (int i = 0; i < scenes.size(); i++) {
                int t = (styleSeed + i * 3) % ROTATION_TRANSITIONS.size();
                scenes.get(i).setTransition(ROTATION_TRANSITIONS.get(t));
            }
            
            // Captions config (burn-in enabled by default for YouTube-ready output)
            VideoCaptionsConfig captionsConfig = new VideoCaptionsConfig();
            captionsConfig.setBurnIn(true);
            captionsConfig.setPreset("youtube");
            captionsConfig.setPosition(0.85);
            captionsConfig.setFontSize(28);
            captionsConfig.setMaxCharsPerLine(42);
            request.setCaptions(captionsConfig);
            
            // Generate chapters from scenes
            List<VideoChapter> chapters = new java.util.ArrayList<>();
            double currentTime = 0;
            for (int i = 0; i < scenes.size(); i++) {
                VideoScene scene = scenes.get(i);
                VideoChapter chapter = new VideoChapter();
                chapter.setTitle("Chapter " + (i + 1) + ": " + (scene.getText() != null ? scene.getText().substring(0, Math.min(50, scene.getText().length())) : "Scene " + (i + 1)));
                chapter.setStartTime(currentTime);
                chapters.add(chapter);
                // Estimate duration from audio or text
                if (scene.getAudioUrl() != null) {
                    // Will be calculated from actual audio duration
                    currentTime += 10; // rough estimate, will be overridden by actual
                } else {
                    currentTime += 8;
                }
            }
            request.setChapters(chapters);

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
                artifact.setReviewStatus(artifactReviewStatus(executionId));
                artifact.setPublicationStatus("UNPUBLISHED");
                artifact.setStorageLocation(response.getVideoUrl());
                artifact.setSize(response.getDurationSeconds());
                artifactStorageService.persist(artifact, missionId);
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

    private String resolveMissionName(UUID missionId) {
        if (missionId == null) return "Karma OS";
        try {
            Mission mission = missionRepository.findById(missionId).orElse(null);
            if (mission != null && mission.getName() != null && !mission.getName().isBlank()) {
                return mission.getName();
            }
        } catch (Exception ignored) {}
        return "Karma OS";
    }

    private String resolveMissionSubtitle(UUID missionId) {
        if (missionId == null) return null;
        try {
            Mission mission = missionRepository.findById(missionId).orElse(null);
            if (mission != null && mission.getDescription() != null && !mission.getDescription().isBlank()) {
                String d = mission.getDescription().trim();
                return d.length() > 120 ? d.substring(0, 120) : d;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isMusicArtifact(Artifact a) {
        if (a.getName() == null) return false;
        String n = a.getName().toLowerCase();
        return n.contains("music") || n.contains("soundtrack") || n.contains("background");
    }

    private String resolveMissionTheme(UUID missionId, UUID executionId) {
        String theme = resolveMissionTheme(missionId);
        if (!"auto".equalsIgnoreCase(theme)) {
            return theme;
        }
        if (executionId == null) {
            return "whiteboard";
        }
        int seed = (executionId.hashCode() & Integer.MAX_VALUE);
        return ROTATION_THEMES.get(seed % ROTATION_THEMES.size());
    }

    private String resolveMissionTheme(UUID missionId) {
        if (missionId == null) return "whiteboard";
        try {
            Mission mission = missionRepository.findById(missionId).orElse(null);
            if (mission != null && mission.getTheme() != null && !mission.getTheme().isBlank()) {
                return mission.getTheme();
            }
        } catch (Exception ignored) {}
        return "whiteboard";
    }

    /** Forces every scene's visual_spec.theme to the mission theme so the video and
     *  /render stills honor the mission-level theme regardless of what the script LLM wrote.
     *  When the mission theme is "auto", a theme is chosen per execution (stable within a
     *  run, rotating across runs). */
    private void applyMissionTheme(List<VideoScene> scenes, UUID missionId, UUID executionId) {
        if (scenes == null || scenes.isEmpty()) return;
        String theme = resolveMissionTheme(missionId, executionId);
        ObjectMapper mapper = new ObjectMapper();
        for (VideoScene scene : scenes) {
            if (scene.getSpec() == null || scene.getSpec().isBlank()) continue;
            try {
                ObjectNode spec = (ObjectNode) mapper.readTree(scene.getSpec());
                spec.put("theme", theme);
                scene.setSpec(spec.toString());
            } catch (Exception ignored) {}
        }
    }

    /**
     * Builds Remotion scenes from the script: each script_body section supplies its
     * visual_spec (or legacy diagram_spec) and the per-scene narration audio matched by
     * sceneIndex. A scene without per-scene audio falls back to the combined narration.
     */
    private List<VideoScene> buildRemotionScenes(String script,
                                                 java.util.Map<Integer, String> sceneAudio,
                                                 String combinedAudio,
                                                 java.util.Map<Integer, String> sceneImages) {
        List<VideoScene> scenes = new java.util.ArrayList<>();
        if (script == null || script.isBlank()) return scenes;

        List<String> specJsons = new java.util.ArrayList<>();
        List<String> speakerTexts = new java.util.ArrayList<>();
        List<String> ssmlMarkers = new java.util.ArrayList<>();
        List<String> visualCues = new java.util.ArrayList<>();
        List<String> timelineEvents = new java.util.ArrayList<>();
        try {
            String jsonCandidate = extractJson(script);
            if (jsonCandidate != null) {
                com.fasterxml.jackson.databind.JsonNode root =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonCandidate);
                com.fasterxml.jackson.databind.JsonNode body = root.get("script_body");
                if (body != null && body.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode section : body) {
                        // V2 versioned structure: visual_spec_v2 or semantic fields at scene level
                        if (section.hasNonNull("visual_spec_v2") && section.get("visual_spec_v2").isObject()) {
                            // Explicit V2 spec — preserve as-is with schemaVersion
                            com.fasterxml.jackson.databind.node.ObjectNode v2 = (com.fasterxml.jackson.databind.node.ObjectNode) section.get("visual_spec_v2").deepCopy();
                            if (!v2.has("schemaVersion")) v2.put("schemaVersion", "v2");
                            specJsons.add(v2.toString());
                        } else if (section.hasNonNull("visual_spec") && section.get("visual_spec").isObject()) {
                            // V1 visual_spec — check if scene also has V2 semantic fields at top level (objects/events/visualIntent)
                            com.fasterxml.jackson.databind.node.ObjectNode vs = (com.fasterxml.jackson.databind.node.ObjectNode) section.get("visual_spec").deepCopy();
                            // Merge V2 semantic fields if present at scene level (objects, events, visualIntent, sceneId)
                            if (section.has("objects") && section.get("objects").isArray()) vs.set("semanticObjects", section.get("objects"));
                            if (section.has("events") && section.get("events").isArray()) vs.set("semanticAnimations", section.get("events"));
                            if (section.hasNonNull("visualIntent")) vs.set("visualIntent", section.get("visualIntent"));
                            if (section.hasNonNull("sceneId")) vs.set("sceneId", section.get("sceneId"));
                            if (section.has("narration") && section.get("narration").isObject() && section.get("narration").has("text")) {
                                // keep narration for reference
                                vs.set("narration", section.get("narration"));
                            }
                            // Mark as V2 if semantic fields were merged
                            if (vs.has("semanticObjects") || vs.has("visualIntent")) vs.put("schemaVersion", "v2");
                            specJsons.add(vs.toString());
                        } else if (section.hasNonNull("diagram_spec") && section.get("diagram_spec").isObject()) {
                            specJsons.add(section.get("diagram_spec").toString());
                        } else if (section.has("objects") || section.has("events") || section.has("visualIntent")) {
                            // Pure V2 scene without legacy visual_spec — create minimal V2 spec from semantic fields
                            com.fasterxml.jackson.databind.node.ObjectNode v2 = objectMapper.createObjectNode();
                            v2.put("schemaVersion", "v2");
                            v2.put("layout", "architecture-diagram");
                            if (section.has("objects")) v2.set("semanticObjects", section.get("objects"));
                            if (section.has("events")) v2.set("semanticAnimations", section.get("events"));
                            if (section.has("visualIntent")) v2.set("visualIntent", section.get("visualIntent"));
                            if (section.has("sceneId")) v2.set("sceneId", section.get("sceneId"));
                            // Also copy narration text to title for fallback
                            if (section.has("narration") && section.get("narration").has("text")) v2.put("title", section.get("narration").get("text").asText().substring(0, Math.min(40, section.get("narration").get("text").asText().length())));
                            specJsons.add(v2.toString());
                        } else {
                            specJsons.add(null);
                        }
                        if (section.hasNonNull("speaker_text")) {
                            speakerTexts.add(section.get("speaker_text").asText());
                        } else {
                            speakerTexts.add(section.toString());
                        }
                        if (section.hasNonNull("ssml_audio_markers")) {
                            ssmlMarkers.add(section.get("ssml_audio_markers").toString());
                        } else {
                            ssmlMarkers.add(null);
                        }
                        if (section.hasNonNull("visual_trigger_cues")) {
                            visualCues.add(section.get("visual_trigger_cues").toString());
                        } else {
                            visualCues.add(null);
                        }
                        if (section.hasNonNull("timeline_events") && section.get("timeline_events").isArray()) {
                            timelineEvents.add(section.get("timeline_events").toString());
                        } else {
                            timelineEvents.add(null);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // not JSON — treated as a single scene below
        }

        if (speakerTexts.isEmpty()) {
            speakerTexts.add(script);
            specJsons.add(null);
        }

        for (int i = 0; i < speakerTexts.size(); i++) {
            VideoScene scene = new VideoScene();
            scene.setText(speakerTexts.get(i));
            String spec = i < specJsons.size() ? specJsons.get(i) : null;
            if (spec != null && !spec.isBlank()) {
                scene.setSpec(spec);
            } else if (sceneImages.containsKey(i)) {
                scene.setImageUrl(sceneImages.get(i));
            }
            if (i < ssmlMarkers.size() && ssmlMarkers.get(i) != null) {
                scene.setSsmlAudioMarkers(ssmlMarkers.get(i));
            }
            if (i < visualCues.size() && visualCues.get(i) != null) {
                scene.setVisualTriggerCues(visualCues.get(i));
            }
            if (i < timelineEvents.size() && timelineEvents.get(i) != null) {
                scene.setTimelineEvents(timelineEvents.get(i));
            }
            
            // Extract captions from script_body section
            try {
                String jsonCandidate = extractJson(script);
                if (jsonCandidate != null) {
                    com.fasterxml.jackson.databind.JsonNode root =
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonCandidate);
                    com.fasterxml.jackson.databind.JsonNode body = root.get("script_body");
                    if (body != null && body.isArray() && i < body.size()) {
                        com.fasterxml.jackson.databind.JsonNode section = body.get(i);
                        if (section.hasNonNull("captions") && section.get("captions").isArray()) {
                            scene.setCaptions(section.get("captions").toString());
                        }
                    }
                }
            } catch (Exception ignored) {}
            
            String perScene = sceneAudio.get(i);
            scene.setAudioUrl(perScene != null ? perScene : combinedAudio);
            scenes.add(scene);
        }
        return scenes;
    }

    private String extractNarrationText(String script) {
        if (script == null || script.isBlank()) return null;
        List<String> speakerTexts = new java.util.ArrayList<>();
        try {
            String jsonCandidate = extractJson(script);
            if (jsonCandidate != null) {
                com.fasterxml.jackson.databind.JsonNode root =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonCandidate);
                com.fasterxml.jackson.databind.JsonNode body = root.get("script_body");
                if (body != null && body.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode section : body) {
                        if (section.hasNonNull("speaker_text")) {
                            speakerTexts.add(section.get("speaker_text").asText());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall back to raw text below
        }
        if (speakerTexts.isEmpty()) {
            return null;
        }
        return String.join("\n\n", speakerTexts);
    }

    /**
     * Returns one narration string per script scene (script_body[].speaker_text),
     * preserving order. Falls back to the raw text as a single scene.
     */
    private List<String> extractSpeakerTexts(String script) {
        List<String> result = new ArrayList<>();
        if (script == null || script.isBlank()) {
            return result;
        }
        try {
            String jsonCandidate = extractJson(script);
            if (jsonCandidate != null) {
                com.fasterxml.jackson.databind.JsonNode root =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonCandidate);
                com.fasterxml.jackson.databind.JsonNode body = root.get("script_body");
                if (body != null && body.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode section : body) {
                        if (section.hasNonNull("speaker_text")) {
                            String st = section.get("speaker_text").asText();
                            if (st != null && !st.isBlank()) {
                                result.add(st);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to raw text below
        }
        if (result.isEmpty()) {
            result.add(script);
        }
        return result;
    }

    private List<VideoScene> buildScenesFromScript(String script, List<String> imageUrls) {
        List<VideoScene> scenes = new java.util.ArrayList<>();
        if (script == null || script.isBlank()) return scenes;

        List<String> speakerTexts = new java.util.ArrayList<>();
        try {
            String jsonCandidate = extractJson(script);
            if (jsonCandidate != null) {
                com.fasterxml.jackson.databind.JsonNode root =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonCandidate);
                com.fasterxml.jackson.databind.JsonNode body = root.get("script_body");
                if (body != null && body.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode section : body) {
                        if (section.hasNonNull("speaker_text")) {
                            speakerTexts.add(section.get("speaker_text").asText());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // not JSON â€” treat whole text as a single scene below
        }

        if (speakerTexts.isEmpty()) {
            speakerTexts.add(script);
        }

        for (int i = 0; i < speakerTexts.size(); i++) {
            VideoScene scene = new VideoScene();
            scene.setText(speakerTexts.get(i));
            if (i < imageUrls.size()) {
                scene.setImageUrl(imageUrls.get(i));
            }
            scenes.add(scene);
        }
        return scenes;
    }

    private String resolveScriptForVideo(UUID executionId, String contextData) {
        try {
            List<ExecutionStep> steps = executionStepRepository.findByExecutionIdOrderByStepOrder(executionId);
            ExecutionStep best = null;
            for (ExecutionStep s : steps) {
                String out = s.getOutputData();
                if (out == null || out.isBlank()) continue;
                String stepType = s.getStepType() != null ? s.getStepType() : "";
                if ("IMAGE_GENERATION".equals(stepType) || "TTS".equals(stepType)
                        || "MUSIC_GENERATION".equals(stepType) || "VIDEO_GENERATION".equals(stepType)
                        || "PUBLISH".equals(stepType)) {
                    continue;
                }
                if (out.contains("\"script_body\"") || out.contains("speaker_text")) {
                    best = s;
                }
            }
            if (best != null && best.getOutputData() != null) {
                return best.getOutputData();
            }
        } catch (Exception e) {
            log.warn("Could not resolve script from execution steps for {}: {}", executionId, e.getMessage());
        }
        if (contextData != null && (contextData.contains("\"script_body\"") || contextData.contains("speaker_text"))) {
            return contextData;
        }
        return contextData != null ? contextData : "";
    }

    private String extractJson(String input) {
        if (input == null) return null;
        String strict = com.karmaos.common.StrictJson.extractLastObjectJson(input);
        if (strict != null) {
            return strict;
        }
        int fence = input.indexOf("```");
        if (fence >= 0) {
            int start = input.indexOf('{', fence);
            int end = input.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return input.substring(start, end + 1);
            }
            int arrStart = input.indexOf('[', fence);
            int arrEnd = input.lastIndexOf(']');
            if (arrStart >= 0 && arrEnd > arrStart) {
                return input.substring(arrStart, arrEnd + 1);
            }
            return null;
        }
        if (input.startsWith("{") || input.startsWith("[")) {
            return input;
        }
        return null;
    }

    private Integer extractSceneIndex(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(metadata);
            if (node.hasNonNull("sceneIndex")) {
                return node.get("sceneIndex").asInt();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private com.fasterxml.jackson.databind.JsonNode extractJsonNode(String input) {
        String candidate = extractJson(input);
        if (candidate == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(candidate);
        } catch (Exception e) {
            return null;
        }
    }

    private Double computeTargetSpeed(UUID missionId, String text) {
        if (missionId == null || text == null || text.isBlank()) return null;
        try {
            Mission mission = missionRepository.findById(missionId).orElse(null);
            if (mission == null || mission.getTargetDurationSeconds() == null) return null;
            int target = mission.getTargetDurationSeconds();
            int words = text.trim().split("\\s+").length;
            if (words == 0) return null;
            double naturalSeconds = words / 150.0 * 60;
            if (naturalSeconds <= 0) return null;
            double speed = naturalSeconds / target;
            // Never slow the narration below natural pace; only speed up when the
            // script would overshoot the target duration.
            if (speed <= 1.15) return null;
            if (speed > 1.8) speed = 1.8;
            return Math.round(speed * 100.0) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to compute narration speed: {}", e.getMessage());
            return null;
        }
    }

    private String synthesizeNarration(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            VoiceProvider voiceProvider = new EdgeTtsProvider();
            VoiceRequest request = new VoiceRequest();
            request.setText(text);
            request.setModel(voiceProvider.getDefaultModel());
            request.setVoice(voiceProvider.getDefaultModel());
            long start = System.currentTimeMillis();
            VoiceResponse response = voiceProvider.synthesize(request);
            long latency = System.currentTimeMillis() - start;
            if (response.isSuccess()) {
                log.info("Scene narration synthesized in {}ms ({} chars)", latency, text.length());
                return response.getAudioUrl();
            }
            log.warn("Scene narration failed: {}", response.getErrorMessage());
        } catch (Exception e) {
            log.warn("Scene narration error: {}", e.getMessage());
        }
        return null;
    }

    public ExecutionStep executePublishStep(UUID executionId, UUID flowId, UUID agentId,
                                            int stepOrder, String contextData, UUID missionId) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "PUBLISH", contextData, missionId);

        try {
            requireAgentSkill(agentId);
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
                        ? "No CONNECTED publishing channels; content produced but not distributed"
                        : publications.stream()
                                .map(p -> p.getDestination() + ": " + p.getErrorMessage())
                                .collect(java.util.stream.Collectors.joining("; "));
                log.warn("Publish step best-effort completion for mission {}: {}", missionId, reason);
                step.setStatus("COMPLETED");
                step.setOutputData("Publish completed (best-effort, no reachable channel): " + reason);
            }
        } catch (Exception e) {
            log.error("Publish step failed", e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    private Provider resolveGeminiImageProvider(UUID agentId) {
        try {
            List<Provider> candidates = providerRepository.findByStatus("CONNECTED").stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                    .toList();
            if (agentId != null) {
                Agent agent = agentRepository.findById(agentId).orElse(null);
                if (agent != null && agent.getDefaultProviderId() != null) {
                    Provider preferred = candidates.stream()
                            .filter(p -> p.getId().equals(agent.getDefaultProviderId()))
                            .filter(p -> isGoogleImageProvider(p))
                            .findFirst().orElse(null);
                    if (preferred != null && !getApiKey(preferred).isEmpty()) {
                        return preferred;
                    }
                }
            }
            Provider gemini = candidates.stream()
                    .filter(this::isGoogleImageProvider)
                    .filter(p -> !getApiKey(p).isEmpty())
                    .findFirst().orElse(null);
            return gemini;
        } catch (Exception e) {
            log.warn("Gemini image provider resolution failed: {}", e.getMessage());
        }
        return null;
    }

    private boolean isGoogleImageProvider(Provider p) {
        if (p == null || p.getProviderType() == null) return false;
        String t = p.getProviderType().toLowerCase();
        return "google".equals(t) || "gemini".equals(t);
    }

    private Provider resolveMissionPreferredImageProvider(UUID missionId) {
        if (missionId == null) return null;
        try {
            Mission mission = missionRepository.findById(missionId).orElse(null);
            if (mission == null || mission.getProviderId() == null) return null;
            Provider preferred = providerRepository.findById(mission.getProviderId()).orElse(null);
            if (preferred == null || preferred.getDeletedAt() != null) return null;
            if (!"CONNECTED".equalsIgnoreCase(preferred.getStatus())) return null;
            if (preferred.getProviderType() != null
                    && IMAGE_PROVIDER_TYPES.contains(preferred.getProviderType().toLowerCase())) {
                return preferred;
            }
        } catch (Exception e) {
            log.warn("Mission image provider resolution failed for mission {}: {}", missionId, e.getMessage());
        }
        return null;
    }

    private Provider resolveAgentPreferredProvider(UUID agentId, Set<String> capabilityTypes) {
        if (agentId == null) return null;
        try {
            Agent agent = agentRepository.findById(agentId).orElse(null);
            if (agent == null || agent.getDefaultProviderId() == null) return null;
            Provider preferred = providerRepository.findById(agent.getDefaultProviderId()).orElse(null);
            if (preferred == null || preferred.getDeletedAt() != null) return null;
            if (!"CONNECTED".equalsIgnoreCase(preferred.getStatus())) return null;
            if (preferred.getProviderType() != null
                    && capabilityTypes.contains(preferred.getProviderType().toLowerCase())) {
                return preferred;
            }
        } catch (Exception e) {
            log.warn("Agent provider resolution failed for agent {}: {}", agentId, e.getMessage());
        }
        return null;
    }

    private Provider resolveProviderForCapability(Set<String> preferredTypes, String defaultType) {
        List<Provider> candidates = providerRepository.findByStatus("CONNECTED").stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                .toList();
        if (preferredTypes.contains("cloudflare")) {
            Provider cloudflare = candidates.stream()
                    .filter(p -> p.getProviderType() != null && "cloudflare".equalsIgnoreCase(p.getProviderType()))
                    .findFirst().orElse(null);
            if (cloudflare != null) {
                return cloudflare;
            }
        }
        if (preferredTypes.contains("google") || preferredTypes.contains("gemini")) {
            Provider google = candidates.stream()
                    .filter(p -> p.getProviderType() != null && ("google".equalsIgnoreCase(p.getProviderType())
                            || "gemini".equalsIgnoreCase(p.getProviderType())))
                    .filter(p -> !getApiKey(p).isEmpty())
                    .findFirst().orElse(null);
            if (google != null) {
                return google;
            }
        }
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
        step = executionStepRepository.save(step);
        return step;
    }

    private void requireAgentSkill(UUID agentId) {
        if (agentId == null) {
            throw new IllegalStateException("Agent is not configured for this media step");
        }
        if (agentSkillRepository.findByIdAgentId(agentId).isEmpty()) {
            throw new IllegalStateException("Agent has no skills assigned. "
                + "Skills are a mandatory behavioral contract; assign a skill before executing this step.");
        }
    }
}
