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
        return getProvider(providerType, apiKey, null);
    }

    public ImageProvider getProvider(String providerType, String apiKey, String apiEndpoint) {
        String key = (providerType != null ? providerType.toLowerCase() : "") + ":" + (apiKey != null ? apiKey.hashCode() : "null") + ":" + (apiEndpoint != null ? apiEndpoint.hashCode() : "null");
        return providerCache.computeIfAbsent(key, k -> createProvider(providerType, apiKey, apiEndpoint));
    }

    public void evictAll() {
        providerCache.clear();
    }

    private ImageProvider createProvider(String providerType, String apiKey, String apiEndpoint) {
        if (providerType == null) return getFallbackProvider();
        return switch (providerType.toLowerCase()) {
            case "google", "gemini" -> new GoogleImageProvider(apiKey, webClientBuilder, objectMapper);
            case "pollinations", "pollinations-image" -> new PollinationsImageProvider(webClientBuilder);
            case "cloudflare" -> new CloudflareWorkersAIImageProvider(apiKey, extractAccountId(apiEndpoint), webClientBuilder);
            default -> {
                log.warn("Unsupported image provider type: {}, using mock", providerType);
                yield getFallbackProvider();
            }
        };
    }

    private static String extractAccountId(String apiEndpoint) {
        if (apiEndpoint == null || apiEndpoint.isBlank()) return "";
        try {
            java.net.URL url = new java.net.URL(apiEndpoint);
            String path = url.getPath() != null ? url.getPath() : "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/accounts/([^/]+)").matcher(path);
            if (m.find()) {
                String id = m.group(1);
                if (id == null || id.isBlank() || id.contains("{") || id.contains("}") || id.contains("account_id")) {
                    return "";
                }
                return id;
            }
        } catch (Exception e) {
            log.warn("Could not extract account_id from apiEndpoint: {}", e.getMessage());
        }
        return "";
    }

    public ImageProvider getFallbackProvider() {
        return new LocalImageProvider();
    }
}
