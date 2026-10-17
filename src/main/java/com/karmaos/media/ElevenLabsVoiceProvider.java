package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ElevenLabs text-to-speech (including cloned voices) via
 * {@code POST /v1/text-to-speech/{voice_id}/with-timestamps}, which returns the audio together
 * with a start/end time for every character. Those are grouped into words so scenes stay in sync
 * with the narration exactly as with Edge TTS.
 *
 * <p>The voice is the account's voice ID (a cloned or library voice), set on the provider as
 * {@code configuration.voiceId}; there is no sensible default voice to fall back to.
 */
public class ElevenLabsVoiceProvider implements VoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsVoiceProvider.class);

    private static final String BASE_URL = "https://api.elevenlabs.io";
    static final String DEFAULT_MODEL = "eleven_multilingual_v2";
    private static final String OUTPUT_FORMAT = "mp3_44100_128";
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElevenLabsVoiceProvider(String apiKey, WebClient.Builder webClientBuilder) {
        this(apiKey, webClientBuilder, BASE_URL);
    }

    /** baseUrl is overridable for tests that stand in for the API. */
    ElevenLabsVoiceProvider(String apiKey, WebClient.Builder webClientBuilder, String baseUrl) {
        this.apiKey = apiKey;
        // clone(): the builder is shared with the other providers; baseUrl would leak into them.
        this.webClient = webClientBuilder.clone().baseUrl(baseUrl).build();
    }

    @Override
    public String getName() { return "ElevenLabs"; }

    @Override
    public String getDefaultModel() { return DEFAULT_MODEL; }

    @Override
    public List<String> getAvailableModels() {
        return List.of(DEFAULT_MODEL, "eleven_turbo_v2_5", "eleven_flash_v2_5");
    }

    @Override
    public List<String> fetchAvailableModels() {
        try {
            JsonNode models = webClient.get().uri("/v1/models").header("xi-api-key", apiKey)
                    .retrieve().bodyToMono(JsonNode.class).block(TIMEOUT);
            List<String> ids = new ArrayList<>();
            if (models != null && models.isArray()) {
                for (JsonNode m : models) {
                    if (m.path("can_do_text_to_speech").asBoolean(false)) ids.add(m.path("model_id").asText());
                }
            }
            return ids.isEmpty() ? getAvailableModels() : ids;
        } catch (Exception e) {
            log.warn("ElevenLabs model discovery failed: {}", e.getMessage());
            return getAvailableModels();
        }
    }

    @Override
    public VoiceResponse synthesize(VoiceRequest request) {
        long start = System.currentTimeMillis();
        String text = request.getText();
        if (text == null || text.isBlank()) return VoiceResponse.error("Text is empty");
        String voiceId = request.getVoice();
        if (voiceId == null || voiceId.isBlank() || "alloy".equals(voiceId)) {
            return VoiceResponse.error("ElevenLabs needs a voice ID: set configuration.voiceId on the provider");
        }
        String model = request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : DEFAULT_MODEL;
        try {
            JsonNode response = webClient.post()
                    .uri(u -> u.path("/v1/text-to-speech/{voiceId}/with-timestamps")
                            .queryParam("output_format", OUTPUT_FORMAT).build(voiceId))
                    .header("xi-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("text", text, "model_id", model))
                    .retrieve().bodyToMono(JsonNode.class).block(TIMEOUT);
            if (response == null || !response.hasNonNull("audio_base64")) {
                return VoiceResponse.error("ElevenLabs returned no audio");
            }
            List<Map<String, Object>> words = wordsFromAlignment(response.path("alignment"));
            double seconds = words.isEmpty() ? 0 : ((Number) words.get(words.size() - 1).get("end")).doubleValue();
            VoiceResponse ok = VoiceResponse.ok("data:audio/mpeg;base64," + response.get("audio_base64").asText(),
                    model, System.currentTimeMillis() - start, (int) Math.ceil(seconds));
            if (!words.isEmpty()) ok.setWordTimingsJson(objectMapper.writeValueAsString(words));
            return ok;
        } catch (WebClientResponseException e) {
            // e.g. 401 bad key, 404 unknown voice, 429 quota or concurrency limit.
            String detail = e.getResponseBodyAsString();
            try {
                JsonNode body = objectMapper.readTree(detail).path("detail");
                if (body.hasNonNull("message")) detail = body.get("message").asText();
                else if (body.isTextual()) detail = body.asText();
            } catch (Exception ignored) {}
            log.warn("ElevenLabs TTS failed: {} {}", e.getStatusCode(), detail);
            return VoiceResponse.error("ElevenLabs error (" + e.getStatusCode().value() + "): " + detail);
        } catch (Exception e) {
            log.warn("ElevenLabs TTS failed: {}", e.getMessage());
            return VoiceResponse.error("ElevenLabs error: " + e.getMessage());
        }
    }

    /**
     * Groups ElevenLabs' per-character alignment (of the original text) into words:
     * [{"text", "start", "end"}] in seconds, the same shape Edge TTS produces.
     */
    static List<Map<String, Object>> wordsFromAlignment(JsonNode alignment) {
        JsonNode chars = alignment.path("characters");
        JsonNode starts = alignment.path("character_start_times_seconds");
        JsonNode ends = alignment.path("character_end_times_seconds");
        List<Map<String, Object>> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        double wordStart = 0;
        double wordEnd = 0;
        for (int i = 0; i < chars.size(); i++) {
            String ch = chars.get(i).asText();
            if (ch.isBlank()) {
                if (word.length() > 0) words.add(Map.of("text", word.toString(), "start", wordStart, "end", wordEnd));
                word.setLength(0);
                continue;
            }
            if (word.length() == 0) wordStart = starts.path(i).asDouble();
            word.append(ch);
            wordEnd = ends.path(i).asDouble();
        }
        if (word.length() > 0) words.add(Map.of("text", word.toString(), "start", wordStart, "end", wordEnd));
        return words;
    }

    @Override
    public boolean testConnection() {
        try {
            webClient.get().uri("/v1/models").header("xi-api-key", apiKey)
                    .retrieve().bodyToMono(JsonNode.class).block(TIMEOUT);
            return true;
        } catch (Exception e) {
            log.warn("ElevenLabs connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
