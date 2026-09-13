package com.karmaos.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenRouter provider — routes requests to 200+ models (GPT, Claude, Llama, Mistral, etc.)
 * via a single OpenAI-compatible API at https://openrouter.ai/api/v1
 * API key from: https://openrouter.ai/keys
 */
public class OpenRouterProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterProvider.class);
    private static final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public OpenRouterProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "openrouter"; }

    @Override
    public String getDefaultModel() { return "google/gemma-2-27b-it:free"; }

    @Override
    public List<String> getAvailableModels() {
        return List.of(
            "google/gemma-2-27b-it:free",
            "google/gemma-2-9b-it:free",
            "meta-llama/llama-3.1-8b-instruct:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "mistralai/mistral-7b-instruct:free",
            "qwen/qwen-2.5-72b-instruct:free"
        );
    }

    @Override
    public List<String> fetchAvailableModels() {
        List<String> result = new ArrayList<>();
        try {
            JsonNode response = webClient.get()
                .uri("https://openrouter.ai/api/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            if (response != null && response.hasNonNull("data")) {
                for (JsonNode m : response.get("data")) {
                    String id = m.path("id").asText("");
                    if (!id.isBlank() && id.endsWith(":free") && !result.contains(id)) {
                        result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("OpenRouter model discovery failed: {}", e.getMessage());
        }
        return result.isEmpty() ? getAvailableModels() : result;
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        long start = System.currentTimeMillis();
        try {
            String model = request.getModel() != null ? request.getModel() : getDefaultModel();

            List<Map<String, String>> messages = new ArrayList<>();
            if (request.getSystemPrompt() != null) {
                messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
            }
            if (request.getUserMessage() != null) {
                messages.add(Map.of("role", "user", "content", request.getUserMessage()));
            }
            if (request.getMessages() != null) {
                messages.addAll(request.getMessages());
            }

            Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", request.getTemperature(),
                "max_tokens", request.getMaxTokens()
            );

            JsonNode response = webClient.post()
                .uri(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("HTTP-Referer", "https://karmaos.ai")
                .header("X-Title", "Karma OS")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            long elapsed = System.currentTimeMillis() - start;

            if (response == null) {
                return LLMResponse.error("Empty response from OpenRouter");
            }

            JsonNode choice = response.get("choices").get(0);
            String content = choice.get("message").get("content").asText();
            JsonNode usage = response.get("usage");
            int promptTokens = usage != null ? usage.get("prompt_tokens").asInt() : 0;
            int completionTokens = usage != null ? usage.get("completion_tokens").asInt() : 0;

            return LLMResponse.ok(content, model, promptTokens, completionTokens, elapsed);

        } catch (Exception e) {
            log.error("OpenRouter API call failed", e);
            return LLMResponse.error("OpenRouter error: " + e.getMessage());
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
