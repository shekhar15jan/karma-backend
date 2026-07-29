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

    public LLMProvider getProvider(String providerType, String apiKey) {
        String key = providerType.toLowerCase() + ":" + (apiKey != null ? apiKey.hashCode() : "null");
        return providerCache.computeIfAbsent(key, k -> createProvider(providerType, apiKey));
    }

    public LLMProvider getProviderForType(String providerType) {
        return getProvider(providerType, null);
    }

    private LLMProvider createProvider(String providerType, String apiKey) {
        String type = providerType.toLowerCase();
        String key = apiKey != null ? apiKey : "";
        return switch (type) {
            case "openai" -> new OpenAiProvider(key, webClientBuilder, objectMapper);
            case "anthropic" -> new AnthropicProvider(key, webClientBuilder, objectMapper);
            case "gemini" -> new GeminiProvider(key, webClientBuilder, objectMapper);
            default -> throw new IllegalArgumentException("Unsupported provider type: " + providerType);
        };
    }

    public LLMProvider getFallbackProvider() {
        return new LLMProvider() {
            @Override public String getName() { return "mock"; }
            @Override public String getDefaultModel() { return "mock-model"; }
            @Override public List<String> getAvailableModels() { return List.of("mock-model"); }
            @Override public LLMResponse chat(LLMRequest request) {
                return LLMResponse.ok(
                    "Mock response for: " + (request.getUserMessage() != null ? request.getUserMessage() : "no message"),
                    "mock-model", 10, 20, 5
                );
            }
            @Override public LLMResponse chatWithStream(LLMRequest request, Consumer<String> onChunk) {
                String msg = "Mock streaming: " + request.getUserMessage();
                onChunk.accept(msg);
                return LLMResponse.ok(msg, "mock-model", 10, 20, 5);
            }
            @Override public boolean testConnection() { return true; }
        };
    }
}
