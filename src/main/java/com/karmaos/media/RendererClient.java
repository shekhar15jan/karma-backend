package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the karma-renderer service (Remotion). Renders narrated, animated
 * technical videos and YouTube thumbnails from structured scene specs.
 */
@Component
public class RendererClient {

    private static final Logger log = LoggerFactory.getLogger(RendererClient.class);

    private final String baseUrl;
    private final Duration renderTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public RendererClient(@Value("${app.renderer.url:http://localhost:3000}") String baseUrl,
                          @Value("${app.renderer.timeout-minutes:45}") long timeoutMinutes,
                          ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.renderTimeout = Duration.ofMinutes(timeoutMinutes);
        this.objectMapper = objectMapper;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            log.warn("Renderer not reachable at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /** Renders an MP4. Scene start times (seconds) come back for YouTube chapter markers. */
    public RenderedVideo renderVideo(Map<String, Object> payload) throws Exception {
        HttpResponse<byte[]> response = post("/video", payload, renderTimeout);
        List<Double> sceneStarts = new ArrayList<>();
        response.headers().firstValue("X-Karma-Scene-Starts").ifPresent(value -> {
            for (String part : value.split(",")) {
                if (!part.isBlank()) sceneStarts.add(Double.parseDouble(part.trim()));
            }
        });
        int duration = response.headers().firstValue("X-Karma-Video-Duration").map(Integer::parseInt).orElse(0);
        return new RenderedVideo(response.body(), duration, sceneStarts);
    }

    public byte[] renderThumbnail(Map<String, Object> payload) throws Exception {
        return post("/thumbnail", payload, Duration.ofMinutes(5)).body();
    }

    private HttpResponse<byte[]> post(String path, Map<String, Object> payload, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            throw new IllegalStateException("Renderer " + path + " returned HTTP " + response.statusCode() + ": "
                    + (body.length() > 500 ? body.substring(0, 500) : body));
        }
        return response;
    }

    public record RenderedVideo(byte[] bytes, int durationSeconds, List<Double> sceneStarts) {}
}
