package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemini Music provider — Lyria 3 via the standard Gemini API
 * (generativelanguage.googleapis.com/v1beta/models/{model}:generateContent).
 * Generates WAV audio returned as a base64 data URI (consistent with voice artifacts).
 */
public class GeminiMusicProvider implements MusicProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiMusicProvider.class);
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final WebClient webClient;
    private final String apiKey;

    public GeminiMusicProvider(String apiKey, WebClient.Builder webClientBuilder) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "Gemini Music (Lyria)"; }

    @Override
    public String getDefaultModel() { return "lyria-3-clip-preview"; }

    @Override
    public List<String> getAvailableModels() {
        return List.of("lyria-3-clip-preview", "lyria-3-pro-preview");
    }

    @Override
    public List<String> fetchAvailableModels() {
        List<String> discovered = discoverLyriaModels();
        return discovered.isEmpty() ? getAvailableModels() : discovered;
    }

    @Override
    public boolean testConnection() {
        if (apiKey.isEmpty()) return false;
        try {
            return !discoverLyriaModels().isEmpty();
        } catch (Exception e) {
            log.warn("Gemini music connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private List<String> discoverLyriaModels() {
        List<String> result = new ArrayList<>();
        if (apiKey.isEmpty()) return result;
        try {
            JsonNode response = webClient.get()
                .uri(API_URL + "?key=" + apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(15));
            if (response != null && response.hasNonNull("models")) {
                for (JsonNode m : response.get("models")) {
                    String id = m.path("name").asText("").replace("models/", "");
                    if (id.startsWith("lyria-") && supportsGenerateContent(m)) {
                        result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Gemini music model discovery failed: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public MusicResponse generate(MusicRequest request) {
        long start = System.currentTimeMillis();
        if (apiKey.isEmpty()) {
            return MusicResponse.error("No API key configured for Gemini Music");
        }
        try {
            String model = request.getModel() != null && !request.getModel().isBlank()
                    ? request.getModel() : getDefaultModel();
            int duration = request.getDurationSeconds() != null ? request.getDurationSeconds() : 30;
            String prompt = request.getPrompt() != null && !request.getPrompt().isBlank()
                    ? request.getPrompt() : "Calm ambient background music";

            Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                    "responseModalities", List.of("AUDIO", "TEXT"),
                    "responseFormat", Map.of("audio", Map.of("mimeType", "audio/wav")),
                    "durationSeconds", Math.min(Math.max(duration, 2), 30)
                ),
                "systemInstruction", Map.of("parts", List.of(Map.of(
                    "text", "You are an expert music composer. Generate a complete, musical composition matching the requested mood, genre, and style. Return a full continuous piece of audio."
                )))
            );

            JsonNode response = webClient.post()
                .uri(API_URL + "/" + model + ":generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(180));

            long latency = System.currentTimeMillis() - start;

            if (response == null) {
                return MusicResponse.error("Empty response from Gemini Music");
            }

            JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
            for (JsonNode part : parts) {
                if (part.hasNonNull("inlineData")) {
                    String mimeType = part.path("inlineData").path("mimeType").asText("audio/wav");
                    String data = part.path("inlineData").path("data").asText("");
                    if (!data.isBlank()) {
                        return MusicResponse.ok("data:" + mimeType + ";base64," + data, model, latency, duration);
                    }
                }
            }
            String text = parts.size() > 0 ? parts.get(0).path("text").asText("") : "";
            return MusicResponse.error("No audio returned by Lyria"
                    + (text.isBlank() ? "" : ": " + truncate(text, 200)));
        } catch (Exception e) {
            log.error("Gemini Music generation failed", e);
            return MusicResponse.error("Gemini Music error: " + e.getMessage());
        }
    }

    private boolean supportsGenerateContent(JsonNode model) {
        JsonNode methods = model.get("supportedGenerationMethods");
        if (methods != null) {
            for (JsonNode method : methods) {
                if (method.asText().contains("generateContent")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
