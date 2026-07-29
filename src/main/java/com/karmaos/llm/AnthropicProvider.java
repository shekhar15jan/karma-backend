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

public class AnthropicProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AnthropicProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "anthropic"; }

    @Override
    public String getDefaultModel() { return "claude-3-5-sonnet-20241022"; }

    @Override
    public List<String> getAvailableModels() {
        return List.of("claude-3-5-sonnet-20241022", "claude-3-opus-20240229", "claude-3-haiku-20240307");
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        long start = System.currentTimeMillis();
        try {
            String model = request.getModel() != null ? request.getModel() : getDefaultModel();

            List<Map<String, String>> messages = new ArrayList<>();
            if (request.getUserMessage() != null) {
                messages.add(Map.of("role", "user", "content", request.getUserMessage()));
            }
            if (request.getMessages() != null) {
                messages.addAll(request.getMessages());
            }

            Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", request.getMaxTokens(),
                "system", request.getSystemPrompt() != null ? request.getSystemPrompt() : "",
                "messages", messages,
                "temperature", request.getTemperature()
            );

            JsonNode response = webClient.post()
                .uri(API_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            long elapsed = System.currentTimeMillis() - start;

            if (response == null) {
                return LLMResponse.error("Empty response from Anthropic");
            }

            String content = response.get("content").get(0).get("text").asText();
            JsonNode usage = response.get("usage");
            int promptTokens = usage != null ? usage.get("input_tokens").asInt() : 0;
            int completionTokens = usage != null ? usage.get("output_tokens").asInt() : 0;

            return LLMResponse.ok(content, model, promptTokens, completionTokens, elapsed);

        } catch (Exception e) {
            log.error("Anthropic API call failed", e);
            return LLMResponse.error("Anthropic error: " + e.getMessage());
        }
    }

    @Override
    public LLMResponse chatWithStream(LLMRequest request, Consumer<String> onChunk) {
        return chat(request);
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
