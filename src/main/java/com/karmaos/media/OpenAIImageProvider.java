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

public class OpenAIImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIImageProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/images/generations";

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAIImageProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() { return "OpenAI DALL-E"; }

    @Override
    public String getDefaultModel() { return "dall-e-3"; }

    @Override
    public List<String> getAvailableModels() { return List.of("dall-e-3", "dall-e-2"); }

    @Override
    public ImageResponse generate(ImageRequest request) {
        if (apiKey.isEmpty()) {
            log.warn("No API key configured for OpenAI image generation, using mock");
            return getFallbackResponse(request);
        }

        long start = System.currentTimeMillis();
        try {
            String model = request.getModel() != null ? request.getModel() : getDefaultModel();
            Map<String, Object> body = Map.of(
                "model", model,
                "prompt", request.getPrompt() != null ? request.getPrompt() : "",
                "n", Math.min(request.getN(), 1),
                "size", request.getSize() != null ? request.getSize() : "1024x1024",
                "quality", request.getQuality() != null ? request.getQuality() : "standard",
                "response_format", "url"
            );

            String jsonBody = objectMapper.writeValueAsString(body);

            String response = webClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));

            long latency = System.currentTimeMillis() - start;
            return parseResponse(response, model, latency);
        } catch (Exception e) {
            log.error("OpenAI image generation failed", e);
            long latency = System.currentTimeMillis() - start;
            return ImageResponse.error("Image generation failed: " + e.getMessage());
        }
    }

    private ImageResponse parseResponse(String responseJson, String model, long latency) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            List<String> urls = new ArrayList<>();
            String revisedPrompt = null;
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    if (item.has("url")) urls.add(item.get("url").asText());
                    if (item.has("revised_prompt")) revisedPrompt = item.get("revised_prompt").asText();
                }
            }
            return ImageResponse.ok(urls, revisedPrompt, model, latency);
        } catch (Exception e) {
            return ImageResponse.error("Failed to parse response: " + e.getMessage());
        }
    }

    private ImageResponse getFallbackResponse(ImageRequest request) {
        return ImageResponse.ok(
            List.of("https://placehold.co/1024x1024?text=" + (request.getPrompt() != null ? request.getPrompt().hashCode() : "image")),
            request.getPrompt(), "mock", 5
        );
    }

    @Override
    public boolean testConnection() {
        if (apiKey.isEmpty()) return false;
        try {
            Map<String, Object> body = Map.of("model", "dall-e-2", "prompt", "test", "n", 1, "size", "256x256");
            webClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(body))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
