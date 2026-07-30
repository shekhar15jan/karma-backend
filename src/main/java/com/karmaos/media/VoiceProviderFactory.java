package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(VoiceProviderFactory.class);

    private final WebClient.Builder webClientBuilder;
    private final Map<String, VoiceProvider> providerCache = new ConcurrentHashMap<>();

    public VoiceProviderFactory(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public VoiceProvider getProvider(String providerType, String apiKey) {
        String key = (providerType != null ? providerType.toLowerCase() : "") + ":" + (apiKey != null ? apiKey.hashCode() : "null");
        return providerCache.computeIfAbsent(key, k -> createProvider(providerType, apiKey));
    }

    private VoiceProvider createProvider(String providerType, String apiKey) {
        if (providerType == null) return getFallbackProvider();
        return switch (providerType.toLowerCase()) {
            case "elevenlabs" -> new ElevenLabsProvider(apiKey, webClientBuilder);
            case "openai" -> new OpenAIVoiceProvider(apiKey, webClientBuilder);
            case "google", "gemini" -> new GoogleVoiceProvider(apiKey, webClientBuilder);
            default -> {
                log.warn("Unsupported voice provider type: {}, using mock", providerType);
                yield getFallbackProvider();
            }
        };
    }

    public VoiceProvider getFallbackProvider() {
        return new VoiceProvider() {
            @Override public String getName() { return "mock-voice"; }
            @Override public String getDefaultModel() { return "mock-model"; }
            @Override public List<String> getAvailableModels() { return List.of("mock-model"); }
            @Override public VoiceResponse synthesize(VoiceRequest request) {
                return VoiceResponse.ok("https://example.com/mock-audio.mp3", "mock-model", 5, 30);
            }
            @Override public boolean testConnection() { return true; }
        };
    }
}
