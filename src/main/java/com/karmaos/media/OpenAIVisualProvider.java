package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI image generation provider (§20) — isolated provider abstraction.
 * No business logic coupling; all visual intent is supplied via {@link ImageRequest}
 * or constructor/provider configuration. Model is NOT hardcoded: read from
 * request or provider configuration (constructor/setter).
 *
 * Configuration supports: model, size, quality, aspectRatio, numberOfImages,
 * referenceImages, transparentBackground.
 *
 * Calls {@code POST https://api.openai.com/v1/images/generations} with Bearer key
 * via WebClient. Compatible with dall-e-2 / dall-e-3 / gpt-image-1.
 */
public class OpenAIVisualProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIVisualProvider.class);
    private static final String OPENAI_URL = "https://api.openai.com/v1/images/generations";
    private static final List<String> KNOWN_MODELS = List.of("gpt-image-1", "dall-e-3", "dall-e-2");

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Configurable defaults (§20)
    private String defaultModel;
    private String defaultSize = "1024x1024";
    private String defaultQuality; // null = omit, let API decide
    private String defaultAspectRatio;
    private int defaultNumberOfImages = 1;
    private List<String> referenceImages;
    private boolean transparentBackground = false;

    public OpenAIVisualProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public OpenAIVisualProvider(String apiKey, WebClient.Builder webClientBuilder) {
        this(apiKey, webClientBuilder, new ObjectMapper());
    }

    // ---- configuration setters (read from provider configuration or request) ----

    public void setModel(String model) { this.defaultModel = model; }
    public void setSize(String size) { this.defaultSize = size; }
    public void setQuality(String quality) { this.defaultQuality = quality; }
    public void setAspectRatio(String aspectRatio) { this.defaultAspectRatio = aspectRatio; }
    public void setNumberOfImages(int n) { this.defaultNumberOfImages = n; }
    public void setReferenceImages(List<String> referenceImages) { this.referenceImages = referenceImages; }
    public void setTransparentBackground(boolean transparentBackground) { this.transparentBackground = transparentBackground; }

    @Override
    public String getName() { return "OpenAI Image"; }

    @Override
    public String getDefaultModel() {
        if (defaultModel != null && !defaultModel.isBlank()) return defaultModel;
        // Fallback is configurable; return first known model only when no config supplied
        return KNOWN_MODELS.get(0);
    }

    @Override
    public List<String> getAvailableModels() {
        if (defaultModel != null && !defaultModel.isBlank()) return List.of(defaultModel);
        return KNOWN_MODELS;
    }

    @Override
    public ImageResponse generate(ImageRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return ImageResponse.error("No API key configured for OpenAI image generation");
        }
        long start = System.currentTimeMillis();
        try {
            String prompt = request.getPrompt() != null ? request.getPrompt().trim() : "";
            if (prompt.isBlank()) {
                return ImageResponse.error("Prompt is required for OpenAI image generation");
            }

            String model = request.getModel() != null && !request.getModel().isBlank()
                    ? request.getModel()
                    : getDefaultModel();
            String size = request.getSize() != null && !request.getSize().isBlank()
                    ? request.getSize()
                    : defaultSize;
            String quality = request.getQuality() != null && !request.getQuality().isBlank()
                    ? request.getQuality()
                    : defaultQuality;
            int n = request.getN() > 0 ? request.getN()
                    : (defaultNumberOfImages > 0 ? defaultNumberOfImages : 1);
            n = Math.max(1, Math.min(n, 4));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("prompt", prompt);
            body.put("model", model);
            body.put("n", n);
            body.put("size", size);
            if (quality != null && !quality.isBlank()) body.put("quality", quality);
            if (transparentBackground) body.put("background", "transparent");
            // aspectRatio / referenceImages are passed when configured
            if (defaultAspectRatio != null && !defaultAspectRatio.isBlank()) {
                // OpenAI gpt-image-1 uses size; keep aspectRatio for provider config parity
                body.put("aspect_ratio", defaultAspectRatio);
            }
            if (referenceImages != null && !referenceImages.isEmpty()) {
                body.put("reference_images", referenceImages);
            }
            // dall-e-3 supports style param; not required here

            String bodyJson = objectMapper.writeValueAsString(body);
            log.debug("OpenAI image request model={} size={} n={}", model, size, n);

            JsonNode response = webClient.post()
                    .uri(OPENAI_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(bodyJson)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(60));

            if (response == null) {
                return ImageResponse.error("OpenAI returned empty response");
            }
            if (response.hasNonNull("error")) {
                String err = response.get("error").path("message").asText(response.get("error").toString());
                log.warn("OpenAI error: {}", err);
                return ImageResponse.error("OpenAI image generation failed: " + err);
            }

            List<String> urls = new ArrayList<>();
            String revisedPrompt = null;
            JsonNode data = response.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    if (item.hasNonNull("url")) urls.add(item.get("url").asText());
                    else if (item.hasNonNull("b64_json")) {
                        String b64 = item.get("b64_json").asText();
                        String mime = "image/png";
                        urls.add("data:" + mime + ";base64," + b64);
                    }
                    if (revisedPrompt == null && item.hasNonNull("revised_prompt")) {
                        revisedPrompt = item.get("revised_prompt").asText();
                    }
                }
            }
            if (urls.isEmpty()) {
                return ImageResponse.error("OpenAI returned no image data");
            }
            long latency = System.currentTimeMillis() - start;
            return ImageResponse.ok(urls, revisedPrompt != null ? revisedPrompt : prompt, model, latency);
        } catch (Exception e) {
            log.warn("OpenAI image generation failed: {}", e.getMessage(), e);
            return ImageResponse.error("OpenAI image generation failed: " + e.getMessage());
        }
    }

    @Override
    public boolean testConnection() {
        ImageRequest probe = new ImageRequest("a minimal premium enterprise background, subtle gradient, no text");
        probe.setModel(getDefaultModel());
        probe.setSize("512x512");
        probe.setN(1);
        ImageResponse resp = generate(probe);
        return resp.isSuccess();
    }
}
