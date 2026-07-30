package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class ElevenLabsProvider implements VoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsProvider.class);
    private static final String API_URL = "https://api.elevenlabs.io/v1/text-to-speech/21m00Tcm4TlvDq8ikWAM";

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElevenLabsProvider(String apiKey, WebClient.Builder webClientBuilder) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "ElevenLabs"; }

    @Override
    public String getDefaultModel() { return "eleven_multilingual_v2"; }

    @Override
    public List<String> getAvailableModels() { return List.of("eleven_multilingual_v2", "eleven_monolingual_v1"); }

    @Override
    public VoiceResponse synthesize(VoiceRequest request) {
        if (apiKey.isEmpty()) {
            log.warn("No API key configured for ElevenLabs, using mock");
            return getFallbackResponse();
        }

        long start = System.currentTimeMillis();
        try {
            String voiceId = request.getVoice() != null ? request.getVoice() : "21m00Tcm4TlvDq8ikWAM";
            String url = "https://api.elevenlabs.io/v1/text-to-speech/" + voiceId;

            Map<String, Object> body = Map.of(
                "text", request.getText() != null ? request.getText() : "",
                "model_id", request.getModel() != null ? request.getModel() : getDefaultModel(),
                "voice_settings", Map.of(
                    "stability", 0.5,
                    "similarity_boost", 0.75
                )
            );

            byte[] audioData = webClient.post()
                .uri(url)
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .bodyValue(objectMapper.writeValueAsString(body))
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(60));

            long latency = System.currentTimeMillis() - start;
            if (audioData == null || audioData.length == 0) {
                return VoiceResponse.error("Empty audio response");
            }

            String dataUri = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(audioData);
            int durationSec = estimateDuration(request.getText() != null ? request.getText() : "", request.getSpeed() != null ? request.getSpeed() : 1.0);
            return VoiceResponse.ok(dataUri, getDefaultModel(), latency, durationSec);
        } catch (Exception e) {
            log.error("ElevenLabs TTS failed", e);
            return VoiceResponse.error("TTS failed: " + e.getMessage());
        }
    }

    private int estimateDuration(String text, double speed) {
        if (text == null || text.isBlank()) return 0;
        int wordCount = text.split("\\s+").length;
        return (int) Math.ceil((wordCount / 150.0) / speed * 60);
    }

    private VoiceResponse getFallbackResponse() {
        return VoiceResponse.ok("https://example.com/mock-audio.mp3", "mock", 5, 30);
    }

    @Override
    public boolean testConnection() {
        if (apiKey.isEmpty()) return false;
        try {
            webClient.get()
                .uri("https://api.elevenlabs.io/v1/voices")
                .header("xi-api-key", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
