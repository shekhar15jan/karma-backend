package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class OpenAIVoiceProvider implements VoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIVoiceProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/audio/speech";

    private final String apiKey;
    private final WebClient webClient;

    public OpenAIVoiceProvider(String apiKey, WebClient.Builder webClientBuilder) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "OpenAI TTS"; }

    @Override
    public String getDefaultModel() { return "tts-1"; }

    @Override
    public List<String> getAvailableModels() { return List.of("tts-1", "tts-1-hd"); }

    @Override
    public VoiceResponse synthesize(VoiceRequest request) {
        if (apiKey.isEmpty()) {
            log.warn("No API key configured for OpenAI TTS, using mock");
            return VoiceResponse.ok("https://example.com/mock-audio.mp3", "mock", 5, 30);
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                "model", request.getModel() != null ? request.getModel() : getDefaultModel(),
                "input", request.getText() != null ? request.getText() : "",
                "voice", request.getVoice() != null ? request.getVoice() : "alloy",
                "response_format", "mp3",
                "speed", request.getSpeed() != null ? request.getSpeed() : 1.0
            );

            String jsonBody = "{\"model\":\"" + body.get("model") + "\",\"input\":\"" +
                ((String) body.get("input")).replace("\"", "\\\"").replace("\n", "\\n") +
                "\",\"voice\":\"" + body.get("voice") + "\",\"response_format\":\"mp3\",\"speed\":" + body.get("speed") + "}";

            byte[] audioData = webClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(60));

            long latency = System.currentTimeMillis() - start;
            if (audioData == null || audioData.length == 0) {
                return VoiceResponse.error("Empty audio response");
            }

            String dataUri = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(audioData);
            int durationSec = estimateDuration(request.getText(), request.getSpeed() != null ? request.getSpeed() : 1.0);
            return VoiceResponse.ok(dataUri, (String) body.get("model"), latency, durationSec);
        } catch (Exception e) {
            log.error("OpenAI TTS failed", e);
            return VoiceResponse.error("TTS failed: " + e.getMessage());
        }
    }

    private int estimateDuration(String text, double speed) {
        if (text == null || text.isBlank()) return 0;
        int wordCount = text.split("\\s+").length;
        return (int) Math.ceil((wordCount / 150.0) / speed * 60);
    }

    @Override
    public boolean testConnection() {
        if (apiKey.isEmpty()) return false;
        try {
            Map<String, Object> body = Map.of("model", "tts-1", "input", "test", "voice", "alloy");
            String jsonBody = "{\"model\":\"tts-1\",\"input\":\"test\",\"voice\":\"alloy\"}";
            webClient.post()
                .uri(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(10));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
