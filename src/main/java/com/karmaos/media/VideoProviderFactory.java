package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.settings.RuntimeSettings;
import com.karmaos.sse.SseController;
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
    private final SseController sseController;
    private final String rendererUrl;
    /** When true, only the Remotion provider is allowed; the FFmpeg and Mock
     *  (fallback) video paths are disabled. Controlled by MEDIA_VIDEO_REMOTION_ONLY. */
    private final boolean remotionOnly;
    private final Map<String, VideoProvider> providerCache = new ConcurrentHashMap<>();

    public VideoProviderFactory(WebClient.Builder webClientBuilder,
                                RuntimeSettings runtimeSettings,
                                ObjectMapper objectMapper,
                                SseController sseController,
                                @Value("${RENDERER_URL:http://localhost:3000}") String rendererUrl,
                                @Value("${media.video-remotion-only:true}") boolean remotionOnly) {
        this.webClientBuilder = webClientBuilder;
        this.runtimeSettings = runtimeSettings;
        this.objectMapper = objectMapper;
        this.sseController = sseController;
        this.rendererUrl = rendererUrl;
        this.remotionOnly = remotionOnly;
    }

    public VideoProvider getProvider(String providerType, String apiKey) {
        String key = (providerType != null ? providerType.toLowerCase() : "") + ":" + (apiKey != null ? apiKey.hashCode() : "null");
        return providerCache.computeIfAbsent(key, k -> createProvider(providerType, apiKey));
    }

    public void evictAll() {
        providerCache.clear();
    }

    private VideoProvider createProvider(String providerType, String apiKey) {
        if (providerType == null) {
            return getFallbackProvider();
        }
        if (remotionOnly && !"remotion".equalsIgnoreCase(providerType)) {
            return disabledProvider(providerType);
        }
        return switch (providerType.toLowerCase()) {
            case "remotion" -> new RemotionVideoProvider(rendererUrl, objectMapper, sseController);
            default -> {
                log.warn("Unsupported video provider type: {}, using mock", providerType);
                yield getFallbackProvider();
            }
        };
    }

    /** Remotion-only mode: non-Remotion providers are blocked with a clear failure. */
    public VideoProvider getFallbackProvider() {
        if (remotionOnly) {
            log.warn("Remotion-only mode is enabled; mock/ffmpeg video fallback is disabled");
            return disabledProvider(null);
        }
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

    private VideoProvider disabledProvider(String providerType) {
        return new VideoProvider() {
            @Override public String getName() { return "disabled-video"; }
            @Override public String getDefaultModel() { return "disabled-model"; }
            @Override public List<String> getAvailableModels() { return List.of("disabled-model"); }
            @Override public VideoResponse generate(VideoRequest request) {
                return VideoResponse.error("Video provider '" + (providerType != null ? providerType : "fallback")
                    + "' is disabled. MEDIA_VIDEO_REMOTION_ONLY=true enforces the Remotion video path only.");
            }
            @Override public boolean testConnection() { return false; }
        };
    }
}
