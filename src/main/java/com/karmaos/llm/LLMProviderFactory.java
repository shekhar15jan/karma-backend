package com.karmaos.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class LLMProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LLMProviderFactory.class);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final Map<String, LLMProvider> providerCache = new ConcurrentHashMap<>();

    public LLMProviderFactory(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public LLMProvider getProvider(String providerType, String apiKey, String apiEndpoint) {
        String key = providerType.toLowerCase() + ":" + (apiKey != null ? apiKey.hashCode() : "null");
        return providerCache.computeIfAbsent(key, k -> createProvider(providerType, apiKey, apiEndpoint));
    }

    public LLMProvider getProvider(String providerType, String apiKey) {
        return getProvider(providerType, apiKey, null);
    }

    public LLMProvider getProviderForType(String providerType) {
        return getProvider(providerType, null, null);
    }

    public void evictAll() {
        providerCache.clear();
    }

    private LLMProvider createProvider(String providerType, String apiKey, String apiEndpoint) {
        String type = providerType.toLowerCase();
        return switch (type) {
            case "openai"      -> new OpenAiProvider(apiKey != null ? apiKey : "", webClientBuilder, objectMapper);
            case "anthropic"   -> new AnthropicProvider(apiKey != null ? apiKey : "", webClientBuilder, objectMapper);
            case "gemini"      -> new GeminiProvider(apiKey != null ? apiKey : "", webClientBuilder, objectMapper);
            case "google"      -> new GeminiProvider(apiKey != null ? apiKey : "", webClientBuilder, objectMapper);
            case "openrouter"  -> new OpenRouterProvider(apiKey != null ? apiKey : "", webClientBuilder, objectMapper);
            case "opencode"    -> new OpenCodeProvider(apiKey != null ? apiKey : "", webClientBuilder, objectMapper, apiEndpoint);
            case "groq"        -> new GroqProvider(apiKey != null ? apiKey : "", webClientBuilder, objectMapper);
            default -> throw new IllegalArgumentException("Unsupported provider type: " + providerType);
        };
    }

    public LLMProvider getFallbackProvider() {
        return new LLMProvider() {
            @Override public String getName() { return "mock"; }
            @Override public String getDefaultModel() { return "mock-model"; }
            @Override public List<String> getAvailableModels() { return List.of("mock-model"); }
            @Override public LLMResponse chat(LLMRequest request) {
                try {
                    String msg = request.getUserMessage() != null ? request.getUserMessage() : "no message";
                    // Truncate to avoid massive DB payloads in mock mode
                    if (msg.length() > 500) msg = msg.substring(0, 500) + "...";
                    String safeMessage = objectMapper.writeValueAsString(msg);
                    String jsonResponse = "{ \"mock\": " + safeMessage + " }";
                    return LLMResponse.ok(jsonResponse, "mock-model", 10, 20, 5);
                } catch (Exception e) {
                    return LLMResponse.ok("{\"mock\":\"fallback\"}", "mock-model", 10, 20, 5);
                }
            }
            @Override public LLMResponse chatWithStream(LLMRequest request, Consumer<String> onChunk) {
                try {
                    String msg = request.getUserMessage() != null ? request.getUserMessage() : "no message";
                    if (msg.length() > 500) msg = msg.substring(0, 500) + "...";
                    String safeMessage = objectMapper.writeValueAsString(msg);
                    String jsonResponse = "{ \"mock\": " + safeMessage + " }";
                    onChunk.accept(jsonResponse);
                    return LLMResponse.ok(jsonResponse, "mock-model", 10, 20, 5);
                } catch (Exception e) {
                    String fallback = "{\"mock\":\"streaming fallback\"}";
                    onChunk.accept(fallback);
                    return LLMResponse.ok(fallback, "mock-model", 10, 20, 5);
                }
            }
            @Override public boolean testConnection() { return true; }
        };
    }
}
