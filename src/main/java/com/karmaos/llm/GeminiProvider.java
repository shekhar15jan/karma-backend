package com.karmaos.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GeminiProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GeminiProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "gemini"; }

    @Override
    public String getDefaultModel() { return "gemini-1.5-pro"; }

    @Override
    public List<String> getAvailableModels() {
        return List.of("gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash");
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        long start = System.currentTimeMillis();
        try {
            String model = request.getModel() != null ? request.getModel() : getDefaultModel();
            String url = API_URL + "/" + model + ":generateContent?key=" + apiKey;

            List<Map<String, Object>> contents = new ArrayList<>();

            String userMsg = request.getUserMessage() != null ? request.getUserMessage() : "";
            if (request.getMessages() != null) {
                for (Map<String, String> msg : request.getMessages()) {
                    contents.add(Map.of(
                        "role", "user".equals(msg.get("role")) ? "user" : "model",
                        "parts", List.of(Map.of("text", msg.get("content")))
                    ));
                }
            }
            contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userMsg))
            ));

            Map<String, Object> requestBody = Map.of(
                "contents", contents,
                "generationConfig", Map.of(
                    "temperature", request.getTemperature(),
                    "maxOutputTokens", request.getMaxTokens()
                ),
                "systemInstruction", request.getSystemPrompt() != null
                    ? Map.of("parts", List.of(Map.of("text", request.getSystemPrompt())))
                    : null
            );

            JsonNode response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            long elapsed = System.currentTimeMillis() - start;

            if (response == null) {
                return LLMResponse.error("Empty response from Gemini");
            }

            JsonNode candidate = response.get("candidates").get(0);
            String content = candidate.get("content").get("parts").get(0).get("text").asText();
            JsonNode usageMetadata = response.get("usageMetadata");
            int promptTokens = usageMetadata != null ? usageMetadata.get("promptTokenCount").asInt() : 0;
            int completionTokens = usageMetadata != null ? usageMetadata.get("candidatesTokenCount").asInt() : 0;

            return LLMResponse.ok(content, model, promptTokens, completionTokens, elapsed);

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            return LLMResponse.error("Gemini error: " + e.getMessage());
        }
    }

    @Override
    public LLMResponse chatWithStream(LLMRequest request, Consumer<String> onChunk) {
        LLMResponse response = chat(request);
        if (onChunk != null && response != null && response.isSuccess() && response.getContent() != null) {
            onChunk.accept(response.getContent());
        }
        return response;
    }

    @Override
    public boolean testConnection() {
        try {
            LLMResponse resp = chat(new LLMRequest("You are a test bot.", "Reply with just: OK"));
            return resp.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }
}
