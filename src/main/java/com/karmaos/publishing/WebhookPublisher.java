package com.karmaos.publishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic HTTP webhook publisher used by custom PUBLISHING channels
 * (e.g. "My Blog Website" pointing at a WP REST /wp-json/v2/posts endpoint).
 * POSTs a JSON payload ({title, description, content, contentType, platform, videoUrl})
 * to the channel endpoint with Bearer auth when a token is configured.
 */
public class WebhookPublisher implements SocialPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebhookPublisher.class);

    private final String platform;
    private final String endpoint;
    private final String authToken;
    private final String contentFormat;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookPublisher(String platform, String endpoint, String authToken,
                            String contentFormat, WebClient.Builder webClientBuilder) {
        this.platform = platform;
        this.endpoint = endpoint;
        this.authToken = authToken;
        this.contentFormat = contentFormat;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getPlatform() { return platform; }

    @Override
    public PublishResponse publish(PublishRequest request) {
        if (endpoint == null || endpoint.isBlank()) {
            return PublishResponse.error("No endpoint configured for channel " + platform);
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", request.getTitle());
            body.put("description", request.getDescription());
            body.put("content", request.getContent());
            body.put("contentType", contentFormat != null && !contentFormat.isBlank() ? contentFormat : "text/markdown");
            body.put("platform", platform);
            if (request.getVideoUrl() != null && !request.getVideoUrl().isBlank()) {
                body.put("videoUrl", request.getVideoUrl());
            }
            if (request.getThumbnailUrl() != null && !request.getThumbnailUrl().isBlank()) {
                body.put("thumbnailUrl", request.getThumbnailUrl());
            }

            WebClient.RequestBodySpec requestBody = webClient.post()
                    .uri(endpoint)
                    .header("Content-Type", "application/json");
            if (authToken != null && !authToken.isBlank()) {
                requestBody = requestBody.header("Authorization", "Bearer " + authToken);
            }

            String response = requestBody
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            String id = null;
            String url = null;
            if (response != null && !response.isBlank()) {
                try {
                    JsonNode root = objectMapper.readTree(response);
                    id = root.has("id") ? root.get("id").asText() : null;
                    url = root.has("url") && !root.get("url").isNull() ? root.get("url").asText() : null;
                    if (root.has("link") && url == null) {
                        url = root.get("link").asText();
                    }
                } catch (Exception ignored) {
                }
            }
            String resolvedUrl = url != null && !url.isBlank() ? url : endpoint;
            return PublishResponse.ok(resolvedUrl, id != null ? id : "webhook-" + System.currentTimeMillis());
        } catch (WebClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            log.warn("Webhook publish to {} failed ({}): {}", platform, e.getStatusCode(), detail);
            return PublishResponse.error("Publish to " + platform + " failed (" + e.getStatusCode() + "): " + detail);
        } catch (Exception e) {
            log.error("Webhook publish to {} failed", platform, e);
            return PublishResponse.error("Publish to " + platform + " failed: " + e.getMessage());
        }
    }

    @Override
    public boolean testConnection() {
        return endpoint != null && !endpoint.isBlank();
    }
}
