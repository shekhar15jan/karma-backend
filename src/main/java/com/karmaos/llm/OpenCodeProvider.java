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
 * OpenCode provider — connects to your locally-running OpenCode instance.
 * OpenCode exposes an OpenAI-compatible API endpoint that lets you use
 * any model configured in your opencode.json (Claude, GPT, Gemini, etc.)
 * through a local proxy on http://localhost:29763
 *
 * API key: use your OpenCode session token or leave blank if no auth is configured.
 * Base URL: configurable via api_endpoint, defaults to http://localhost:29763/v1
 */
public class OpenCodeProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeProvider.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:29763/v1/chat/completions";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;

    public OpenCodeProvider(String apiKey, WebClient.Builder webClientBuilder, ObjectMapper objectMapper, String apiEndpoint) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
        this.baseUrl = (apiEndpoint != null && !apiEndpoint.isBlank())
            ? apiEndpoint + "/chat/completions"
            : DEFAULT_BASE_URL;
    }

    @Override
    public String getName() { return "opencode"; }

    @Override
    public String getDefaultModel() { return "claude-sonnet-4-5"; }

    @Override
    public List<String> getAvailableModels() {
        // Models available depend on your opencode.json configuration
        return List.of(
            "claude-sonnet-4-5",
            "claude-opus-4-5",
            "gpt-4o",
            "gpt-4o-mini",
            "gemini-2.0-flash",
            "gemini-1.5-pro"
        );
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

            var requestSpec = webClient.post()
                .uri(baseUrl)
                .contentType(MediaType.APPLICATION_JSON);

            // Include API key only if provided
            if (apiKey != null && !apiKey.isBlank()) {
                requestSpec = requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }

            JsonNode response = requestSpec
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            long elapsed = System.currentTimeMillis() - start;

            if (response == null) {
                return LLMResponse.error("Empty response from OpenCode");
            }

            JsonNode choice = response.get("choices").get(0);
            String content = choice.get("message").get("content").asText();
            JsonNode usage = response.get("usage");
            int promptTokens = usage != null ? usage.get("prompt_tokens").asInt() : 0;
            int completionTokens = usage != null ? usage.get("completion_tokens").asInt() : 0;

            return LLMResponse.ok(content, model, promptTokens, completionTokens, elapsed);

        } catch (Exception e) {
            log.error("OpenCode API call failed", e);
            return LLMResponse.error("OpenCode error: " + e.getMessage());
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
            log.warn("OpenCode connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
