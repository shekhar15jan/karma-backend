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
