package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real image generation via Google Gemini.
 *
 * <p>Uses the provider's configured free-tier multimodal models as candidates
 * (e.g. gemini-2.5-flash, gemini-3.5-flash) rather than hardcoding dedicated
 * image-only model names. Falls back to a small built-in image-model list only
 * when no configured models are supplied. On any failure the orchestrator is
 * expected to retry with the Pollinations fallback (keyless, free).</p>
 */
public class GoogleImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleImageProvider.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private static final List<String> FALLBACK_IMAGE_MODELS = List.of(
            "gemini-3.1-flash-image",
            "gemini-2.5-flash-image",
            "gemini-3-pro-image",
            "gemini-3.1-flash-image-preview",
            "gemini-3-pro-image-preview"
    );

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<String> configuredModels;

    public GoogleImageProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.webClient = webClientBuilder.build();
    }

    public void setConfiguredModels(List<String> configuredModels) {
        this.configuredModels = configuredModels;
    }

    private List<String> effectiveModels() {
        if (configuredModels != null && !configuredModels.isEmpty()) {
            List<String> result = new ArrayList<>();
            // 1) free-tier multimodal flash models that can return inline images
            configuredModels.stream()
                    .filter(m -> {
                        String lm = m.toLowerCase();
                        return lm.contains("flash") && !lm.contains("-image") && !lm.startsWith("imagen");
                    })
                    .forEach(m -> { if (!result.contains(m)) result.add(m); });
            // 2) dedicated image-generation models
            configuredModels.stream()
                    .filter(m -> m.toLowerCase().contains("image") || m.toLowerCase().startsWith("imagen"))
                    .forEach(m -> { if (!result.contains(m)) result.add(m); });
            return result.isEmpty() ? configuredModels : result;
        }
        return FALLBACK_IMAGE_MODELS;
    }

    @Override
    public String getName() { return "Google Gemini Image"; }

    @Override
    public String getDefaultModel() { return effectiveModels().get(0); }

    @Override
    public List<String> getAvailableModels() { return effectiveModels(); }

    @Override
    public List<String> fetchAvailableModels() {
        List<String> result = new ArrayList<>();
        try {
            JsonNode response = webClient.get()
                .uri(BASE_URL + "?key=" + apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(15));
            if (response != null && response.hasNonNull("models")) {
                for (JsonNode m : response.get("models")) {
                    String id = m.path("name").asText("").replace("models/", "");
                    if (id.contains("image")) {
                        result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Gemini model discovery failed: {}", e.getMessage());
        }
        return result.isEmpty() ? effectiveModels() : result;
    }

    @Override
    public ImageResponse generate(ImageRequest request) {
        if (apiKey.isEmpty()) {
            return ImageResponse.error("No API key configured for image generation");
        }
        long start = System.currentTimeMillis();
        try {
            JsonNode response = generateImage(request);
            List<String> urls = new ArrayList<>();
            JsonNode content = response.path("candidates").path(0).path("content").path("parts");
            if (content.isArray()) {
                for (JsonNode part : content) {
                    JsonNode inline = part.get("inlineData");
                    if (inline != null && inline.hasNonNull("mimeType") && inline.hasNonNull("data")) {
                        urls.add("data:" + inline.get("mimeType").asText() + ";base64," + inline.get("data").asText());
                    }
                }
            }
            if (urls.isEmpty()) {
                return ImageResponse.error("Gemini returned no image data (no inlineData in candidates)");
            }
            String model = request.getModel() != null ? request.getModel() : getDefaultModel();
            long latency = System.currentTimeMillis() - start;
            return ImageResponse.ok(urls, request.getPrompt(), model, latency);
        } catch (ImageGenerationException e) {
            log.warn("Image generation failed: {}", e.getMessage());
            return ImageResponse.error("Image generation failed: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Image generation failed (base): {}", e.getMessage(), e);
            return ImageResponse.error("Image generation failed: " + e.getMessage());
        }
    }

    private JsonNode generateImage(ImageRequest request) {
        List<String> attempted = new ArrayList<>();
        RuntimeException last = null;
        for (String model : imageCandidates(request)) {
            try {
                Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text",
                            request.getPrompt() != null ? request.getPrompt() : ""))
                    )),
                    "generationConfig", Map.of(
                        "responseModalities", List.of("IMAGE", "TEXT"),
                        "candidateCount", Math.max(1, Math.min(request.getN(), 4))
                    )
                );
                JsonNode response = webClient.post()
                    .uri(BASE_URL + "/" + model + ":generateContent?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(60));
                if (response == null) {
                    throw new ImageGenerationException("null response from " + model);
                }
                if (response.hasNonNull("error")) {
                    throw new ImageGenerationException(model + " -> " + response.get("error").toString());
                }
                return response;
            } catch (ImageGenerationException e) {
                attempted.add(model + " -> " + e.getMessage());
                log.warn("Gemini image candidate {} failed: {}", model, e.getMessage());
                last = e;
            } catch (Exception e) {
                attempted.add(model);
                log.warn("Gemini image candidate {} threw: {}", model, e.getMessage());
                last = new ImageGenerationException(model + " -> " + e.getMessage());
            }
        }
        throw new ImageGenerationException("all candidate models failed: " + attempted + " | last=" + (last != null ? last.getMessage() : ""));
    }

    private List<String> imageCandidates(ImageRequest request) {
        List<String> candidates = new ArrayList<>();
        if (request.getModel() != null && !request.getModel().isBlank()) {
            candidates.add(request.getModel());
        }
        if (candidates.isEmpty()) {
            candidates.addAll(effectiveModels());
        }
        return candidates;
    }

    @Override
    public boolean testConnection() {
        ImageRequest probe = new ImageRequest("a single red circle on a white background, minimal");
        probe.setSize("256x256");
        probe.setN(1);
        probe.setModel(effectiveModels().get(0));
        ImageResponse resp = generate(probe);
        return resp.isSuccess();
    }

    private static final class ImageGenerationException extends RuntimeException {
        ImageGenerationException(String message) {
            super(message);
        }
    }
}