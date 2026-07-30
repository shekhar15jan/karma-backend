package com.karmaos.publishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

public class YouTubePublisher implements SocialPublisher {

    private static final Logger log = LoggerFactory.getLogger(YouTubePublisher.class);
    private static final String API_URL = "https://www.googleapis.com/youtube/v3";

    private final String apiKey;
    private final String accessToken;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YouTubePublisher(String apiKey, String accessToken, WebClient.Builder webClientBuilder) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.accessToken = accessToken != null ? accessToken : "";
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getPlatform() { return "youtube"; }

    @Override
    public PublishResponse publish(PublishRequest request) {
        if (accessToken.isEmpty() && apiKey.isEmpty()) {
            log.warn("No credentials configured for YouTube, using mock");
            return PublishResponse.ok("https://youtube.com/watch?v=mock123", "mock123");
        }

        try {
            String snippet = "{"
                + "\"title\":\"" + escapeJson(request.getTitle() != null ? request.getTitle() : "Untitled") + "\","
                + "\"description\":\"" + escapeJson(request.getDescription() != null ? request.getDescription() : "") + "\","
                + "\"categoryId\":\"" + (request.getCategoryId() != null ? request.getCategoryId() : "22") + "\""
                + "}";
            String status = "\"privacyStatus\":\"" + (request.getPrivacyStatus() != null ? request.getPrivacyStatus() : "private") + "\"";
            String body = "{\"snippet\":" + snippet + ",\"status\":{" + status + "}}";

            String authHeader = !accessToken.isEmpty() ? "Bearer " + accessToken : "Bearer " + apiKey;

            String response = webClient.post()
                .uri(API_URL + "/videos?part=snippet,status")
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));

            JsonNode root = objectMapper.readTree(response);
            String videoId = root.has("id") ? root.get("id").asText() : null;
            if (videoId != null && !videoId.isEmpty()) {
                return PublishResponse.ok("https://youtube.com/watch?v=" + videoId, videoId);
            }
            String error = root.has("error") ? root.get("error").toString() : "Unknown error";
            return PublishResponse.error("YouTube upload failed: " + error);
        } catch (Exception e) {
            log.error("YouTube publish failed", e);
            return PublishResponse.error("YouTube publish failed: " + e.getMessage());
        }
    }

    @Override
    public boolean testConnection() {
        if (apiKey.isEmpty() && accessToken.isEmpty()) return false;
        try {
            String auth = !accessToken.isEmpty() ? "Bearer " + accessToken : "Bearer " + apiKey;
            webClient.get()
                .uri(API_URL + "/channels?part=id&mine=true")
                .header("Authorization", auth)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
