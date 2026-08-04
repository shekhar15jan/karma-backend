package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Keyless image generation via Pollinations (https://image.pollinations.ai).
 *
 * <p>No API key is required, and the returned image is a real generated PNG delivered as a
 * {@code data:image/png;base64,...} URL. On any failure this provider returns an
 * {@link ImageResponse#error} — it never silently degrades to placeholder/branded tiles.
 */
public class PollinationsImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(PollinationsImageProvider.class);
    private static final String BASE_URL = "https://image.pollinations.ai/prompt";

    private final WebClient webClient;

    public PollinationsImageProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "Pollinations"; }

    @Override
    public String getDefaultModel() { return "flux"; }

    @Override
    public List<String> getAvailableModels() { return List.of("flux", "turbo"); }

    @Override
    public ImageResponse generate(ImageRequest request) {
        long start = System.currentTimeMillis();
        try {
            String prompt = request.getPrompt() != null ? request.getPrompt().trim() : "";
            if (prompt.isEmpty()) {
                return ImageResponse.error("Empty prompt for image generation");
            }
            int[] dims = parseSize(request.getSize());
            int seed = Math.abs(prompt.hashCode()) % 100000;
            String encodedPrompt = UriUtils.encode(prompt, StandardCharsets.UTF_8);
            String uri = BASE_URL + "/" + encodedPrompt
                    + "?width=" + dims[0] + "&height=" + dims[1]
                    + "&nologo=true&model=" + request.getModel() + "&seed=" + seed;

            byte[] bytes = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(Duration.ofSeconds(60));

            if (bytes == null || bytes.length < 100) {
                return ImageResponse.error("Pollinations returned an empty/invalid image response");
            }
            String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            long latency = System.currentTimeMillis() - start;
            return ImageResponse.ok(List.of(dataUrl), prompt, getDefaultModel(), latency);
        } catch (Exception e) {
            log.warn("Pollinations image generation failed: {}", e.getMessage());
            return ImageResponse.error("Pollinations image generation failed: " + e.getMessage());
        }
    }

    @Override
    public boolean testConnection() {
        ImageRequest probe = new ImageRequest("a single red circle on a white background, minimal");
        probe.setSize("256x256");
        return generate(probe).isSuccess();
    }

    private int[] parseSize(String size) {
        if (size != null && size.matches("\\d+x\\d+")) {
            String[] parts = size.split("x");
            try {
                return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
            } catch (NumberFormatException ignored) {}
        }
        return new int[] { 1024, 1024 };
    }
}
