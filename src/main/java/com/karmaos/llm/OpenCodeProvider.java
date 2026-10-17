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

public class OpenCodeProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeProvider.class);

    private static final String DEFAULT_BASE_URL = "https://opencode.ai/zen/v1/chat/completions";

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
    public String getDefaultModel() { return "deepseek-v4-flash-free"; }

    @Override
    public List<String> getAvailableModels() {
        // Live catalog at https://opencode.ai/zen/v1/models (66 ids, 2026-08-27). Free tier = *-free + big-pickle.
        // Keep hardcoded fallback in sync with live free tier so fallback chain + UI dropdown work without network.
        return List.of(
            "muse-spark-1.2-contributor-free",
            "deepseek-v4-flash-free",
            "big-pickle",
            "mimo-v2.5-free",
            "laguna-s-2.1-free",
            "ling-3.0-flash-free",
            "north-mini-code-free",
            "nemotron-3-ultra-free",
            "nemotron-3.5-lightning-free",
            "hy3-free"
        );
    }

    @Override
    public List<String> fetchAvailableModels() {
        List<String> result = new ArrayList<>();
        try {
            String modelsUrl = baseUrl;
            if (modelsUrl.endsWith("/chat/completions")) {
                modelsUrl = modelsUrl.substring(0, modelsUrl.length() - "/chat/completions".length());
            }
            if (!modelsUrl.endsWith("/models")) {
                modelsUrl = modelsUrl + "/models";
            }
            var requestSpec = webClient.get().uri(modelsUrl);
            if (apiKey != null && !apiKey.isBlank()) {
                requestSpec = requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            JsonNode response = requestSpec.retrieve().bodyToMono(JsonNode.class).block();
            if (response != null && response.hasNonNull("data")) {
                for (JsonNode m : response.get("data")) {
                    String id = m.path("id").asText("");
                    if (!id.isBlank() && !result.contains(id)) {
                        result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("OpenCode model discovery failed: {}", e.getMessage());
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

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", request.getTemperature());
            body.put("max_tokens", request.getMaxTokens());
            if (request.getAdditionalParams() != null) {
                for (Map.Entry<String, Object> entry : request.getAdditionalParams().entrySet()) {
                    body.put(entry.getKey(), entry.getValue());
                }
            }

            var requestSpec = webClient.post()
                .uri(baseUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "opencode/1.18.16");

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

            if (response.hasNonNull("error")) {
                String errMsg = response.get("error").path("message").asText("OpenCode error");
                log.warn("OpenCode returned error: {}", errMsg);
                return LLMResponse.error(errMsg);
            }

            JsonNode choice = response.get("choices").get(0);
            String content = choice.get("message").get("content").asText();
            if (content.isBlank() && choice.get("message").hasNonNull("reasoning_content")) {
                content = choice.get("message").get("reasoning_content").asText();
            }
            JsonNode usage = response.get("usage");
            int promptTokens = usage != null ? usage.get("prompt_tokens").asInt() : 0;
            int completionTokens = usage != null ? usage.get("completion_tokens").asInt() : 0;

            if (content.isBlank()) {
                log.warn("OpenCode returned empty content (usage={} prompt, {} completion). Raw: {}",
                        promptTokens, completionTokens, response.toString());
                return LLMResponse.error("Empty content returned by OpenCode model " + model);
            }

            return LLMResponse.ok(content, model, promptTokens, completionTokens, elapsed);

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            // The status line alone ("400 Bad Request") hides the reason, e.g. the free tier being
            // restricted to the OpenCode app or a retired model id; surface OpenCode's own message.
            String detail = e.getResponseBodyAsString();
            try {
                JsonNode err = objectMapper.readTree(detail).path("error");
                if (err.hasNonNull("message")) detail = err.get("message").asText();
            } catch (Exception ignored) {}
            log.error("OpenCode API call failed: {} {}", e.getStatusCode(), detail);
            return LLMResponse.error("OpenCode error (" + e.getStatusCode().value() + "): " + detail);
        } catch (Exception e) {
            log.error("OpenCode API call failed", e);
            return LLMResponse.error("OpenCode error: " + e.getMessage());
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
            log.warn("OpenCode connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
