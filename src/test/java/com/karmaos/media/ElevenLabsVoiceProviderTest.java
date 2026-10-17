package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ElevenLabsVoiceProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    /** Stands in for the API; records the request and answers with the given status and body. */
    private String serve(int status, String body, AtomicReference<String> seen) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seen.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " key="
                    + exchange.getRequestHeaders().getFirst("xi-api-key") + " body="
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static VoiceRequest request(String voice) {
        VoiceRequest r = new VoiceRequest("Hi Redis.");
        r.setVoice(voice);
        return r;
    }

    @Test
    void synthesizesWithTheConfiguredVoiceAndReturnsWordTimings() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        String base = serve(200, """
                {"audio_base64": "SUQz", "alignment": {
                  "characters": ["H","i"," ","R","e","d","i","s","."],
                  "character_start_times_seconds": [0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8],
                  "character_end_times_seconds":   [0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9]}}""", seen);

        VoiceResponse response = new ElevenLabsVoiceProvider("key-1", WebClient.builder(), base).synthesize(request("voice-42"));

        assertTrue(response.isSuccess(), response.getErrorMessage());
        assertEquals("data:audio/mpeg;base64,SUQz", response.getAudioUrl());
        assertTrue(seen.get().startsWith("POST /v1/text-to-speech/voice-42/with-timestamps?output_format=mp3_44100_128 key=key-1"), seen.get());
        assertTrue(seen.get().contains("\"model_id\":\"eleven_multilingual_v2\""), seen.get());
        JsonNode words = mapper.readTree(response.getWordTimingsJson());
        assertEquals("Hi", words.get(0).get("text").asText());
        assertEquals("Redis.", words.get(1).get("text").asText());
        assertEquals(0.3, words.get(1).get("start").asDouble(), 1e-9);
        assertEquals(0.9, words.get(1).get("end").asDouble(), 1e-9);
    }

    @Test
    void reportsTheApiErrorMessage() throws Exception {
        String base = serve(401, "{\"detail\": {\"status\": \"invalid_api_key\", \"message\": \"Invalid API key\"}}", new AtomicReference<>());

        VoiceResponse response = new ElevenLabsVoiceProvider("bad", WebClient.builder(), base).synthesize(request("voice-42"));

        assertFalse(response.isSuccess());
        assertEquals("ElevenLabs error (401): Invalid API key", response.getErrorMessage());
    }

    @Test
    void refusesToGuessAVoice() {
        VoiceResponse response = new ElevenLabsVoiceProvider("key", WebClient.builder()).synthesize(new VoiceRequest("Hi"));

        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMessage().contains("configuration.voiceId"));
    }

    @Test
    void groupsCharactersIntoWordsAcrossRepeatedSpaces() throws Exception {
        JsonNode alignment = mapper.readTree("""
                {"characters": [" ","a"," "," ","b","c"],
                 "character_start_times_seconds": [0,1,2,3,4,5],
                 "character_end_times_seconds":   [1,2,3,4,5,6]}""");

        List<Map<String, Object>> words = ElevenLabsVoiceProvider.wordsFromAlignment(alignment);

        assertEquals(2, words.size());
        assertEquals(Map.of("text", "bc", "start", 4.0, "end", 6.0), words.get(1));
    }
}
