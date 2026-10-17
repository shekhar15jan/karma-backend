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

    public void evictAll() {
        providerCache.clear();
    }

    private VoiceProvider createProvider(String providerType, String apiKey) {
        if (providerType == null) return getFallbackProvider();
        return switch (providerType.toLowerCase()) {
            case "google", "gemini" -> new GoogleVoiceProvider(apiKey, webClientBuilder);
            case "edge-tts", "edge" -> new EdgeTtsProvider();
            case "elevenlabs" -> new ElevenLabsVoiceProvider(apiKey, webClientBuilder);
            default -> {
                log.warn("Unsupported voice provider type: {}, using mock", providerType);
                yield getFallbackProvider();
            }
        };
    }

    /**
     * Used when no voice provider is connected or a type is unsupported. Edge TTS is real and
     * keyless; the previous mock "succeeded" with https://example.com/mock-audio.mp3, so videos
     * were rendered without narration on a default install.
     */
    public VoiceProvider getFallbackProvider() {
        return new EdgeTtsProvider();
    }
}
