package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.agent.Agent;
import com.karmaos.agent.AgentRepository;
import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.artifact.ArtifactStorageService;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.execution.ExecutionService;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.publication.Publication;
import com.karmaos.publishing.PublishRequest;
import com.karmaos.publishing.PublishingService;
import com.karmaos.settings.RuntimeSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Set<String> IMAGE_PROVIDER_TYPES = Set.of("google", "gemini", "pollinations", "pollinations-image", "cloudflare");
    private static final Set<String> VOICE_PROVIDER_TYPES = Set.of("google", "gemini", "edge-tts", "edge");
    private static final Set<String> VIDEO_PROVIDER_TYPES = Set.of("ffmpeg");
    private static final Set<String> MUSIC_PROVIDER_TYPES = Set.of("google-music");
    private static final Map<String, String> CAPABILITY_PROVIDER_MAP = Map.of(
        "image_generation", "google",
        "audio_generation", "edge-tts",
        "music_generation", "google-music"
    );

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
                                      MissionRepository missionRepository) {
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
    }

    public ExecutionStep executeImageStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "IMAGE_GENERATION", contextData, missionId);

        try {
            Provider primaryProvider = resolveMissionPreferredImageProvider(missionId);
            if (primaryProvider == null) {
                primaryProvider = resolveGeminiImageProvider(agentId);
            }
            if (primaryProvider == null) {
                primaryProvider = resolveAgentPreferredProvider(agentId, IMAGE_PROVIDER_TYPES);
                if (primaryProvider == null) {
                    primaryProvider = resolveProviderForCapability(IMAGE_PROVIDER_TYPES, "pollinations");
                }
            }
            if (primaryProvider == null) {
                log.error("Image step failed: no image-capable provider is configured");
                step.setStatus("FAILED");
                step.setErrorMessage("No image generation provider is configured");
                step.setCompletedAt(LocalDateTime.now());
                return executionService.saveStep(executionId, step);
            }
            boolean keylessImage = isKeylessImage(primaryProvider);
            String apiKey = getApiKey(primaryProvider);
            if (!keylessImage && apiKey.isEmpty()) {
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
            ImageProvider fallbackProvider;
            if (isKeylessImage(primaryProvider)) {
                fallbackProvider = primaryImageProvider;
            } else {
                fallbackProvider = imageProviderFactory.getProvider("pollinations", null);
            }
            ImageProvider localFallbackProvider = imageProviderFactory.getFallbackProvider();

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
                String sceneUrl = tryGenerateScene(primaryImageProvider, fallbackProvider, imagePrompts.get(i), i, missionSeed);
                if (sceneUrl != null) {
                    allUrls.add(sceneUrl);
                    persistImageArtifact(missionId, sceneUrl, i);
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
            log.warn("Fallback Pollinations also failed for scene {}: {}", sceneIndex, response.getErrorMessage());
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

    private void persistImageArtifact(UUID missionId, String url, int sceneIndex) {
        Artifact artifact = new Artifact();
        artifact.setMissionId(missionId);
        artifact.setName("Generated Scene Image " + (sceneIndex + 1));
        artifact.setArtifactType("MEDIA_ASSET");
        artifact.setStatus("COMPLETED");
        artifact.setContentText(url);
        artifact.setContentType(resolveImageContentType(url));
        artifact.setReviewStatus("PENDING");
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
            // not JSON â€” treat as single prompt below
        }
        return List.of(contextData);
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
            java.util.Map<String, String> characters = new java.util.LinkedHashMap<>();
            if (root.has("characters") && root.get("characters").isArray()) {
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
            } else {
                List<String> masterKeys = new ArrayList<>();
                if (root.has("script_body") && root.get("script_body").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode s : root.get("script_body")) {
                        for (String k : List.of("image_prompt", "visual_description")) {
                            if (s.hasNonNull(k) && !s.get(k).asText().isBlank()) {
                                masterKeys.add(s.get(k).asText());
                            }
                        }
                    }
                }
                String joined = String.join(" \n ", masterKeys);
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(?i)\\b([A-Z][a-z]+) (the|the) ([A-Z][a-z]+)\\b")
                        .matcher(joined);
                while (m.find()) {
                    characters.putIfAbsent(m.group(1) + " the " + m.group(3), null);
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
                artifactStorageService.persist(artifact, missionId);
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

    public ExecutionStep executeMusicStep(UUID executionId, UUID flowId, UUID agentId,
                                          int stepOrder, String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = createStep(executionId, flowId, agentId, stepOrder, "MUSIC_GENERATION", contextData, missionId);

        try {
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
                artifact.setReviewStatus("PENDING");
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
            Provider provider = resolveProviderForCapability(VIDEO_PROVIDER_TYPES, "ffmpeg");
            VideoProvider videoProvider = provider != null
                ? videoProviderFactory.getProvider(provider.getProviderType(), getApiKey(provider))
                : videoProviderFactory.getFallbackProvider();

            VideoRequest request = new VideoRequest();
            request.setTitle("Video for Mission " + missionId.toString().substring(0, 8));
            String script = contextData != null ? contextData : "";
            if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
                script += "\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n" + reviewerFeedback;
            }
            request.setScript(script);

            // Collect existing audio and image artifacts (images ordered by sceneIndex)
            List<Artifact> artifacts = artifactRepository.findByMissionIdOrderByCreatedAtDesc(missionId);
            List<String> imageUrls = new java.util.ArrayList<>();
            String audioUrl = null;
            java.util.Map<Integer, String> sceneImages = new java.util.TreeMap<>();

            for (Artifact a : artifacts) {
                if ("AUDIO".equals(a.getArtifactType()) && audioUrl == null) {
                    audioUrl = a.getStorageLocation();
                } else if ("MEDIA_ASSET".equals(a.getArtifactType()) && isImageContentType(a.getContentType())) {
                    if (a.getStorageLocation() != null) {
                        Integer sceneIndex = extractSceneIndex(a.getMetadata());
                        if (sceneIndex != null) {
                            sceneImages.putIfAbsent(sceneIndex, a.getStorageLocation());
                        } else {
                            sceneImages.putIfAbsent(sceneImages.size(), a.getStorageLocation());
                        }
                    }
                }
            }
            imageUrls.addAll(sceneImages.values());
            if (imageUrls.isEmpty()) {
                log.error("Video composition failed: no images produced upstream for the video (no fallback imagery will be used)");
                step.setStatus("FAILED");
                step.setErrorMessage("Video composition has no usable images upstream; image step must run first");
                step.setCompletedAt(LocalDateTime.now());
                return executionService.saveStep(executionId, step);
            }
            request.setAudioUrl(audioUrl);
            request.setImageUrls(imageUrls);

            List<VideoScene> scenes = buildScenesFromScript(script, imageUrls);
            request.setScenes(scenes);

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
            String narrationUrl = synthesizeNarration(speakerTexts.get(i));
            if (narrationUrl != null) {
                scene.setAudioUrl(narrationUrl);
            }
            scenes.add(scene);
        }
        return scenes;
    }

    private String extractJson(String input) {
        if (input == null) return null;
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

    private boolean isKeylessImage(Provider provider) {
        if (provider == null || provider.getProviderType() == null) return false;
        String t = provider.getProviderType().toLowerCase();
        return "pollinations".equals(t) || "pollinations-image".equals(t);
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
