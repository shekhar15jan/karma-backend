package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MusicProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(MusicProviderFactory.class);

    private final WebClient.Builder webClientBuilder;
    private final Map<String, MusicProvider> providerCache = new ConcurrentHashMap<>();

    public MusicProviderFactory(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public MusicProvider getProvider(String providerType, String apiKey) {
        String key = (providerType != null ? providerType.toLowerCase() : "") + ":" + (apiKey != null ? apiKey.hashCode() : "null");
        return providerCache.computeIfAbsent(key, k -> createProvider(providerType, apiKey));
    }

    public void evictAll() {
        providerCache.clear();
    }

    private MusicProvider createProvider(String providerType, String apiKey) {
        if (providerType == null) return getFallbackProvider();
        return switch (providerType.toLowerCase()) {
            case "google-music", "google", "gemini" -> new GeminiMusicProvider(apiKey, webClientBuilder);
            default -> {
                log.warn("Unsupported music provider type: {}, using mock", providerType);
                yield getFallbackProvider();
            }
        };
    }

    public MusicProvider getFallbackProvider() {
        return new MusicProvider() {
            @Override public String getName() { return "mock-music"; }
            @Override public String getDefaultModel() { return "mock-model"; }
            @Override public List<String> getAvailableModels() { return List.of("mock-model"); }
            @Override public List<String> fetchAvailableModels() { return List.of("mock-model"); }
            @Override public MusicResponse generate(MusicRequest request) {
                return MusicResponse.ok("https://example.com/mock-music.wav", "mock-model", 5, 30);
            }
            @Override public boolean testConnection() { return true; }
        };
    }
}
