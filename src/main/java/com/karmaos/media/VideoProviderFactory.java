package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.settings.RuntimeSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VideoProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(VideoProviderFactory.class);

    private final WebClient.Builder webClientBuilder;
    private final RuntimeSettings runtimeSettings;
    private final ObjectMapper objectMapper;
    private final String rendererUrl;
    private final Map<String, VideoProvider> providerCache = new ConcurrentHashMap<>();

    public VideoProviderFactory(WebClient.Builder webClientBuilder,
                                RuntimeSettings runtimeSettings,
                                ObjectMapper objectMapper,
                                @Value("${RENDERER_URL:http://localhost:3000}") String rendererUrl) {
        this.webClientBuilder = webClientBuilder;
        this.runtimeSettings = runtimeSettings;
        this.objectMapper = objectMapper;
        this.rendererUrl = rendererUrl;
    }

    public VideoProvider getProvider(String providerType, String apiKey) {
        String key = (providerType != null ? providerType.toLowerCase() : "") + ":" + (apiKey != null ? apiKey.hashCode() : "null");
        return providerCache.computeIfAbsent(key, k -> createProvider(providerType, apiKey));
    }

    public void evictAll() {
        providerCache.clear();
    }

    private VideoProvider createProvider(String providerType, String apiKey) {
        if (providerType == null) return getFallbackProvider();
        return switch (providerType.toLowerCase()) {
            case "ffmpeg" -> new FFmpegVideoProvider(runtimeSettings);
            case "remotion" -> new RemotionVideoProvider(rendererUrl, objectMapper);
            default -> {
                log.warn("Unsupported video provider type: {}, using mock", providerType);
                yield getFallbackProvider();
            }
        };
    }

    public VideoProvider getFallbackProvider() {
        return new VideoProvider() {
            @Override public String getName() { return "mock-video"; }
            @Override public String getDefaultModel() { return "mock-model"; }
            @Override public List<String> getAvailableModels() { return List.of("mock-model"); }
            @Override public VideoResponse generate(VideoRequest request) {
                return VideoResponse.ok("https://example.com/mock-video.mp4", 15);
            }
            @Override public boolean testConnection() { return true; }
        };
    }
}
