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

public class GoogleVoiceProvider implements VoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleVoiceProvider.class);
    private static final String API_URL = "https://texttospeech.googleapis.com/v1";

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleVoiceProvider(String apiKey, WebClient.Builder webClientBuilder) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getName() { return "Google Cloud TTS"; }

    @Override
    public String getDefaultModel() { return "en-US-Neural2-D"; }

    @Override
    public List<String> getAvailableModels() {
        return List.of("en-US-Neural2-D", "en-US-Neural2-F", "en-US-Studio-Q", "en-GB-Neural2-B", "ja-JP-Neural2-B");
    }

    @Override
    public VoiceResponse synthesize(VoiceRequest request) {
        if (apiKey.isEmpty()) {
            log.warn("No API key configured for Google Cloud TTS, using mock");
            return VoiceResponse.ok("https://example.com/mock-audio.mp3", "mock", 5, 30);
        }

        long start = System.currentTimeMillis();
        try {
            String voiceName = request.getVoice() != null ? request.getVoice() : "en-US-Neural2-D";
            String languageCode = voiceName.length() >= 5 ? voiceName.substring(0, 5).replace("-", "-") : "en-US";

            Map<String, Object> voice = Map.of(
                "languageCode", languageCode,
                "name", voiceName
            );

            Map<String, Object> audioConfig = Map.of(
                "audioEncoding", "MP3",
                "speakingRate", request.getSpeed() != null ? request.getSpeed() : 1.0
            );

            Map<String, Object> input = Map.of(
                "text", request.getText() != null ? request.getText() : ""
            );

            Map<String, Object> body = Map.of(
                "input", input,
                "voice", voice,
                "audioConfig", audioConfig
            );

            String url = API_URL + "/text:synthesize?key=" + apiKey;

            JsonNode response = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(30));

            long latency = System.currentTimeMillis() - start;

            if (response == null || !response.has("audioContent")) {
                return VoiceResponse.error("Empty TTS response");
            }

            String audioContent = response.get("audioContent").asText();
            String dataUri = "data:audio/mpeg;base64," + audioContent;

            int durationSec = estimateDuration(request.getText(), request.getSpeed() != null ? request.getSpeed() : 1.0);
            return VoiceResponse.ok(dataUri, voiceName, latency, durationSec);
        } catch (Exception e) {
            log.error("Google Cloud TTS failed", e);
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
            Map<String, Object> body = Map.of(
                "input", Map.of("text", "test"),
                "voice", Map.of("languageCode", "en-US", "name", "en-US-Neural2-D"),
                "audioConfig", Map.of("audioEncoding", "MP3")
            );
            webClient.post()
                .uri(API_URL + "/text:synthesize?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(body))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
