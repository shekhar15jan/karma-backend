package com.karmaos.publishing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocialPublisherFactory {

    private static final Logger log = LoggerFactory.getLogger(SocialPublisherFactory.class);

    private final WebClient.Builder webClientBuilder;
    private final Map<String, SocialPublisher> publisherCache = new ConcurrentHashMap<>();

    public SocialPublisherFactory(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public SocialPublisher getPublisher(String platform, String apiKey, String accessToken) {
        String key = (platform != null ? platform.toLowerCase() : "") + ":" + (apiKey != null ? apiKey.hashCode() : "0") + ":" + (accessToken != null ? accessToken.hashCode() : "0");
        return publisherCache.computeIfAbsent(key, k -> createPublisher(platform, apiKey, accessToken));
    }

    /**
     * Returns a publisher for a connected PUBLISHING channel. Custom channels (any platform
     * other than youtube) with an HTTP endpoint get a {@link WebhookPublisher}; youtube uses
     * the real YouTube publisher; anything without usable config falls back to mock.
     */
    public SocialPublisher getPublisherForChannel(String platform, String endpoint, String authToken, String contentFormat) {
        String key = "ch:" + (platform != null ? platform.toLowerCase() : "") + ":" + (endpoint != null ? endpoint.hashCode() : "0")
                + ":" + (authToken != null ? authToken.hashCode() : "0") + ":" + (contentFormat != null ? contentFormat.hashCode() : "0");
        return publisherCache.computeIfAbsent(key, k -> {
            String p = platform != null ? platform.toLowerCase() : "";
            if ("youtube".equals(p)) {
                return new YouTubePublisher(authToken, authToken, webClientBuilder);
            }
            if (endpoint != null && !endpoint.isBlank()) {
                return new WebhookPublisher(p.isBlank() ? "custom" : p, endpoint, authToken, contentFormat, webClientBuilder);
            }
            log.warn("Publishing channel {} has no endpoint, using mock", p);
            return getFallbackPublisher();
        });
    }

    private SocialPublisher createPublisher(String platform, String apiKey, String accessToken) {
        if (platform == null) return getFallbackPublisher();
        return switch (platform.toLowerCase()) {
            case "youtube" -> new YouTubePublisher(apiKey, accessToken, webClientBuilder);
            default -> {
                log.warn("Unsupported publishing platform: {}, using mock", platform);
                yield getFallbackPublisher();
            }
        };
    }

    public SocialPublisher getFallbackPublisher() {
        return new SocialPublisher() {
            @Override public String getPlatform() { return "mock"; }
            @Override public PublishResponse publish(PublishRequest request) {
                return PublishResponse.ok("https://example.com/mock-post", "mock-" + System.currentTimeMillis());
            }
            @Override public boolean testConnection() { return true; }
        };
    }
}
