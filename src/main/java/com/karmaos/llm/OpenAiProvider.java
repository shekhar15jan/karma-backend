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

public class OpenAiProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public OpenAiProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "openai"; }

    @Override
    public String getDefaultModel() { return "gpt-4o"; }

    @Override
    public List<String> getAvailableModels() {
        return List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo");
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
                .uri(API_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            long elapsed = System.currentTimeMillis() - start;

            if (response == null) {
                return LLMResponse.error("Empty response from OpenAI");
            }

            JsonNode choice = response.get("choices").get(0);
            String content = choice.get("message").get("content").asText();
            JsonNode usage = response.get("usage");
            int promptTokens = usage != null ? usage.get("prompt_tokens").asInt() : 0;
            int completionTokens = usage != null ? usage.get("completion_tokens").asInt() : 0;

            return LLMResponse.ok(content, model, promptTokens, completionTokens, elapsed);

        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            return LLMResponse.error("OpenAI error: " + e.getMessage());
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
