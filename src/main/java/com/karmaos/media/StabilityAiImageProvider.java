package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class StabilityAiImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(StabilityAiImageProvider.class);
    private final String apiKey;

    public StabilityAiImageProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getName() { return "Stability AI"; }

    @Override
    public String getDefaultModel() { return "sd3.5"; }

    @Override
    public List<String> getAvailableModels() { return List.of("sd3.5", "sd3", "core"); }

    @Override
    public boolean testConnection() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public ImageResponse generate(ImageRequest request) {
        log.info("Generating image with Stability AI: {}", request.getPrompt());
        try {
            Thread.sleep(2000);
            return ImageResponse.ok(
                List.of("https://example.com/stability-mock-image.png"),
                request.getPrompt(),
                getDefaultModel(),
                1
            );
        } catch (Exception e) {
            log.error("Failed to generate image with Stability AI", e);
            return ImageResponse.error(e.getMessage());
        }
    }
}
