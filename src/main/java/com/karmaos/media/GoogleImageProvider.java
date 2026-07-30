package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class GoogleImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleImageProvider.class);
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String IMAGEN_URL = "https://us-central1-aiplatform.googleapis.com/v1/projects";

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleImageProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "Google Imagen"; }

    @Override
    public String getDefaultModel() { return "imagen-4.0-generate-001"; }

    @Override
    public List<String> getAvailableModels() { return List.of("imagen-4.0-generate-001", "imagen-4.0-fast-generate-001", "imagen-4.0-ultra-generate-001"); }

    @Override
    public ImageResponse generate(ImageRequest request) {
        if (apiKey.isEmpty()) {
            log.warn("No API key configured for Google Imagen, using mock");
            return getFallbackResponse(request);
        }

        long start = System.currentTimeMillis();
        try {
            String model = request.getModel() != null ? request.getModel() : "imagen-4.0-generate-001";
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":predict?key=" + apiKey;

            Map<String, Object> instances = Map.of(
                "prompt", request.getPrompt() != null ? request.getPrompt() : ""
            );

            Map<String, Object> parameters = Map.of(
                "sampleCount", Math.min(request.getN(), 4),
                "aspectRatio", mapSizeToAspectRatio(request.getSize())
            );

            Map<String, Object> body = Map.of(
                "instances", List.of(instances),
                "parameters", parameters
            );

            String response = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(body))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));

            long latency = System.currentTimeMillis() - start;
            return parseResponse(response, model, latency);
        } catch (Exception e) {
            log.error("Google Imagen generation failed, trying Gemini native image gen", e);
            long latency = System.currentTimeMillis() - start;
            try {
                return tryGeminiNativeImageGen(request, latency);
            } catch (Exception ex) {
                return ImageResponse.error("Image generation failed: " + e.getMessage());
            }
        }
    }

    private ImageResponse tryGeminiNativeImageGen(ImageRequest request, long latency) {
        String model = "gemini-2.5-flash";
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
            + model + ":generateContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of(
                    "text", "Generate an image: " + (request.getPrompt() != null ? request.getPrompt() : "")
                ))
            )),
            "generationConfig", Map.of(
                "temperature", 1.0,
                "maxOutputTokens", 8192
            )
        );

        try {
            JsonNode response = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(60));

            if (response != null && response.has("candidates")) {
                JsonNode parts = response.get("candidates").get(0).get("content").get("parts");
                for (JsonNode part : parts) {
                    if (part.has("inlineData")) {
                        String mimeType = part.get("inlineData").get("mimeType").asText();
                        String data = part.get("inlineData").get("data").asText();
                        String url2 = "data:" + mimeType + ";base64," + data;
                        return ImageResponse.ok(List.of(url2), request.getPrompt(), model, latency);
                    }
                }
            }
            return ImageResponse.error("No image data in Gemini response");
        } catch (Exception e) {
            return ImageResponse.error("Gemini native image gen failed: " + e.getMessage());
        }
    }

    private ImageResponse parseResponse(String responseJson, String model, long latency) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            List<String> urls = new java.util.ArrayList<>();
            JsonNode predictions = root.get("predictions");
            if (predictions != null && predictions.isArray()) {
                for (JsonNode pred : predictions) {
                    if (pred.has("bytesBase64Encoded")) {
                        String b64 = pred.get("bytesBase64Encoded").asText();
                        urls.add("data:image/png;base64," + b64);
                    } else if (pred.has("mimeType") && pred.has("data")) {
                        urls.add("data:" + pred.get("mimeType").asText() + ";base64," + pred.get("data").asText());
                    }
                }
            }
            if (urls.isEmpty()) {
                JsonNode generatedImages = root.get("generatedImages");
                if (generatedImages != null && generatedImages.isArray()) {
                    for (JsonNode img : generatedImages) {
                        if (img.has("image") && img.get("image").has("bytesBase64Encoded")) {
                            String b64 = img.get("image").get("bytesBase64Encoded").asText();
                            urls.add("data:image/png;base64," + b64);
                        }
                    }
                }
            }
            if (urls.isEmpty()) return ImageResponse.error("No images in response");
            return ImageResponse.ok(urls, requestPrompt(root), model, latency);
        } catch (Exception e) {
            return ImageResponse.error("Parse failed: " + e.getMessage());
        }
    }

    private String requestPrompt(JsonNode root) {
        try {
            if (root.has("predictions") && root.get("predictions").isArray() && root.get("predictions").size() > 0) {
                JsonNode first = root.get("predictions").get(0);
                if (first.has("prompt")) return first.get("prompt").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String mapSizeToAspectRatio(String size) {
        if (size == null) return "1:1";
        return switch (size) {
            case "1024x1024" -> "1:1";
            case "1024x576", "1920x1080" -> "16:9";
            case "576x1024", "1080x1920" -> "9:16";
            case "1024x768" -> "4:3";
            case "768x1024" -> "3:4";
            default -> "1:1";
        };
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
            webClient.get()
                .uri("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
