package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Image generation via Cloudflare Workers AI.
 *
 * <p>Uses the Cloudflare AI REST API to run open-source image models
 * (FLUX.1 Schnell, FLUX.2 Dev, SDXL, DreamShaper) at the edge.
 * Requires a Cloudflare API Token with {@code Workers AI: Run} permission
 * and an account ID provided via the provider's apiEndpoint URL
 * (e.g. {@code https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run}).</p>
 */
public class CloudflareWorkersAIImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(CloudflareWorkersAIImageProvider.class);

    private static final List<String> AVAILABLE_MODELS = List.of(
            "@cf/black-forest-labs/flux-1-schnell",
            "@cf/black-forest-labs/flux-2-dev",
            "@cf/stabilityai/stable-diffusion-xl-base-1.0",
            "@cf/stabilityai/dreamshaper-8-lcm"
    );

    private static final String DEFAULT_MODEL = AVAILABLE_MODELS.get(0);

    private static final int MAX_BUFFER_BYTES = 20 * 1024 * 1024;

    private final String apiToken;
    private final String accountId;
    private final WebClient webClient;

    public CloudflareWorkersAIImageProvider(String apiToken, String accountId, WebClient.Builder webClientBuilder) {
        this.apiToken = apiToken != null ? apiToken : "";
        this.accountId = accountId != null ? accountId : "";
        this.webClient = webClientBuilder
                .codecs(config -> config.defaultCodecs().maxInMemorySize(MAX_BUFFER_BYTES))
                .build();
    }

    @Override
    public String getName() { return "Cloudflare Workers AI"; }

    @Override
    public String getDefaultModel() { return DEFAULT_MODEL; }

    @Override
    public List<String> getAvailableModels() { return AVAILABLE_MODELS; }

    @Override
    public List<String> fetchAvailableModels() {
        List<String> result = new ArrayList<>();
        if (accountId.isEmpty() || apiToken.isEmpty()) return AVAILABLE_MODELS;
        try {
            com.fasterxml.jackson.databind.JsonNode response = webClient.get()
                .uri("https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/models")
                .header("Authorization", "Bearer " + apiToken)
                .retrieve()
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                .block(Duration.ofSeconds(15));
            if (response != null && response.hasNonNull("result")) {
                com.fasterxml.jackson.databind.JsonNode models = response.get("result");
                if (models.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode m : models) {
                        String id = m.path("id").asText("");
                        if (!id.isEmpty()) result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Cloudflare model discovery failed: {}", e.getMessage());
        }
        return result.isEmpty() ? AVAILABLE_MODELS : result;
    }

    @Override
    public ImageResponse generate(ImageRequest request) {
        if (apiToken.isEmpty()) {
            return ImageResponse.error("No API token configured for Cloudflare Workers AI");
        }
        if (accountId.isEmpty()) {
            return ImageResponse.error("Account ID is required; set apiEndpoint to " +
                "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run");
        }
        String requestedModel = request.getModel() != null ? request.getModel() : DEFAULT_MODEL;
        long start = System.currentTimeMillis();
        String model = requestedModel;
        ImageGenerationException last = null;
        for (int round = 0; round < 3; round++) {
            byte[] imageBytes = null;
            ImageGenerationException roundError = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    imageBytes = generateImage(request, model);
                    break;
                } catch (ImageGenerationException e) {
                    roundError = e;
                    log.warn("Cloudflare attempt {} failed on model {}: {}", attempt + 1, model, e.getMessage());
                    if (isContentBlocked(e.getMessage()) || isAuthFailure(e.getMessage())) {
                        break;
                    }
                    sleepQuietly(1200L * (attempt + 1));
                } catch (Exception e) {
                    roundError = new ImageGenerationException(e.getMessage() == null ? "transient error" : e.getMessage());
                    log.warn("Cloudflare transient failure on model {}: {}", model, roundError.getMessage());
                    sleepQuietly(1200L * (attempt + 1));
                }
            }
            if (imageBytes != null) {
                String mime = mimeFor(imageBytes);
                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                String dataUrl = "data:" + mime + ";base64," + base64;
                long latency = System.currentTimeMillis() - start;
                return ImageResponse.ok(List.of(dataUrl), request.getPrompt(), model, latency);
            }
            last = roundError;
            if (roundError != null && !isContentBlocked(roundError.getMessage())) {
                break;
            }
            String next = alternateModel(model);
            if (next == null) {
                break;
            }
            model = next;
        }
        return ImageResponse.error("Cloudflare image generation failed: "
            + (last != null ? last.getMessage() : "no usable model produced an image"));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isAuthFailure(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        return m.contains("401") || m.contains("unauthorized") || m.contains("authentication");
    }

    private static boolean isContentBlocked(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        return m.contains("nsfw") || m.contains("content") || m.contains("filter") || m.contains("safety") || m.contains("blocked");
    }

    private static String alternateModel(String current) {
        for (String m : AVAILABLE_MODELS) {
            if (!m.equals(current)) {
                return m;
            }
        }
        return null;
    }

    private byte[] generateImage(ImageRequest request, String model) {
        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/" + model;
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        String prompt = request.getPrompt() != null ? request.getPrompt() : "";
        if (prompt.length() > 2040) {
            prompt = prompt.substring(Math.max(0, prompt.length() - 2040));
        }
        body.put("prompt", prompt);
        if (request.getSeed() != null) {
            body.put("seed", request.getSeed());
        }
        body.put("num_steps", 20);
        body.put("guidance", 3.5);
        try {
            return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .exchangeToMono(resp -> {
                    int status = resp.statusCode().value();
                    return resp.bodyToMono(byte[].class).map(bytes -> {
                        byte[] rawBody = bytes != null ? bytes : new byte[0];
                        if (rawBody.length == 0) {
                            return new Result(status, rawBody, null);
                        }
                        if (looksLikeImage(rawBody)) {
                            return new Result(status, rawBody, null);
                        }
                        byte[] embedded = extractEmbeddedImage(rawBody);
                        if (embedded != null) {
                            return new Result(status, embedded, null);
                        }
                        String errorText = extractErrorText(rawBody);
                        return new Result(status, rawBody, errorText);
                    });
                })
                .block(Duration.ofSeconds(120))
                .validate(model);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String detail = extractCloudflareError(e);
            throw new ImageGenerationException(
                "Cloudflare returned " + e.getStatusCode().value() + " for model " + model
                + (detail == null ? "" : ": " + detail));
        }
    }

    private static boolean looksLikeImage(byte[] bytes) {
        if (bytes.length >= 8) {
            if (bytes[0] == (byte)0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return true;
            if (bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8) return true;
        }
        return false;
    }

    private static String mimeFor(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte)0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 4 && bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return "image/png";
    }

    private static byte[] extractEmbeddedImage(byte[] bytes) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
            com.fasterxml.jackson.databind.JsonNode result = node.path("result");
            String image = result.path("image").asText("");
            if (image == null || image.isBlank()) return null;
            int comma = image.indexOf(',');
            String encoded = comma >= 0 ? image.substring(comma + 1) : image;
            return Base64.getDecoder().decode(encoded);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractErrorText(byte[] bytes) {
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (text.isBlank()) return "empty response";
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
            if (node.has("result") && node.get("result").hasNonNull("image")) {
                return null;
            }
            if (node.hasNonNull("errors") && node.get("errors").isArray() && node.get("errors").size() > 0) {
                String msg = node.get("errors").get(0).path("message").asText("");
                String code = node.get("errors").get(0).path("code").asText("");
                return ((code.isBlank() ? "" : "code " + code + "; ") + msg);
            }
            String trimmed = text.trim();
            return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
        } catch (Exception e) {
            String trimmed = text.trim();
            return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
        }
    }

    private static final class Result {
        final int status;
        final byte[] bytes;
        final String error;
        Result(int status, byte[] bytes, String error) {
            this.status = status;
            this.bytes = bytes;
            this.error = error;
        }
        byte[] validate(String model) {
            if (status >= 400) {
                throw new ImageGenerationException("Cloudflare returned " + status + " for model " + model + (error == null ? "" : ": " + error));
            }
            if (bytes == null || bytes.length == 0 || error != null) {
                throw new ImageGenerationException("Cloudflare returned " + status + " for model " + model + (error == null ? " (empty body)" : ": " + error));
            }
            return bytes;
        }
    }

    private static String extractCloudflareError(org.springframework.web.reactive.function.client.WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            if (node.hasNonNull("errors") && node.get("errors").isArray() && node.get("errors").size() > 0) {
                String msg = node.get("errors").get(0).path("message").asText("");
                String code = node.get("errors").get(0).path("code").asText("");
                return (code.isBlank() ? "" : "code " + code + "; ") + msg;
            }
            if (node.hasNonNull("result") && node.get("result").hasNonNull("errors")) {
                String msg = node.get("result").get("errors").get(0).path("message").asText("");
                if (!msg.isBlank()) return msg;
            }
        } catch (Exception ignored) { }
        String trimmed = body.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }

    @Override
    public boolean testConnection() {
        ImageRequest probe = new ImageRequest("a scenic mountain landscape with a clear blue sky, green pine trees and a calm lake reflecting the sunlight, wide-angle cinematic composition");
        probe.setSize("512x512");
        probe.setN(1);
        probe.setModel(DEFAULT_MODEL);
        ImageResponse resp = generate(probe);
        return resp.isSuccess();
    }

    private static final class ImageGenerationException extends RuntimeException {
        ImageGenerationException(String message) {
            super(message);
        }
    }
}