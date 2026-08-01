package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ImageProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(ImageProviderFactory.class);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final Map<String, ImageProvider> providerCache = new ConcurrentHashMap<>();

    public ImageProviderFactory(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public ImageProvider getProvider(String providerType, String apiKey) {
        String key = (providerType != null ? providerType.toLowerCase() : "") + ":" + (apiKey != null ? apiKey.hashCode() : "null");
        return providerCache.computeIfAbsent(key, k -> createProvider(providerType, apiKey));
    }

    public void evictAll() {
        providerCache.clear();
    }

    private ImageProvider createProvider(String providerType, String apiKey) {
        if (providerType == null) return getFallbackProvider();
        return switch (providerType.toLowerCase()) {
            case "openai" -> new OpenAIImageProvider(apiKey, webClientBuilder, objectMapper);
            case "google", "gemini" -> new GoogleImageProvider(apiKey, webClientBuilder, objectMapper);
            case "stability" -> new StabilityAiImageProvider(apiKey);
            default -> {
                log.warn("Unsupported image provider type: {}, using mock", providerType);
                yield getFallbackProvider();
            }
        };
    }

    public ImageProvider getFallbackProvider() {
        return new LocalImageProvider();
    }
}
