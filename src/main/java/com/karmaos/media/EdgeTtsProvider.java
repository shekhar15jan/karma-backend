package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EdgeTtsProvider implements VoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(EdgeTtsProvider.class);

    private static final List<String> AVAILABLE_VOICES = List.of(
        "en-US-AriaNeural", "en-US-JennyNeural", "en-US-GuyNeural",
        "en-GB-SoniaNeural", "en-GB-RyanNeural",
        "en-IN-NeerjaNeural", "en-IN-NeerjaExpressiveNeural", "en-IN-PrabhatNeural",
        "hi-IN-SwaraNeural", "hi-IN-MadhurNeural",
        "ta-IN-PallaviNeural", "ta-IN-ValluvarNeural",
        "te-IN-ShrutiNeural", "te-IN-MohanNeural",
        "bn-IN-TanishaaNeural", "bn-IN-BashkarNeural",
        "mr-IN-AarohiNeural", "mr-IN-ManoharNeural",
        "gu-IN-DhwaniNeural", "gu-IN-NiranjanNeural",
        "kn-IN-SapnaNeural", "kn-IN-GaganNeural",
        "ml-IN-SobhanaNeural", "ml-IN-MidhunNeural",
        "ur-IN-GulNeural", "ur-IN-SalmanNeural",
        "fr-FR-DeniseNeural", "de-DE-KatjaNeural",
        "ja-JP-NanamiNeural", "zh-CN-XiaoxiaoNeural"
    );

    private static final String DEFAULT_VOICE = "en-IN-PrabhatNeural";
    private static final String EDGE_TTS_CMD = "python";
    private static final String EDGE_TTS_MODULE = "-m";
    private static final String EDGE_TTS_MODULE_NAME = "edge_tts";

    /**
     * Synthesises with the edge_tts library rather than its CLI: only the library reports a
     * timestamp per spoken word (the CLI writes sentence subtitles), and the renderer needs
     * those to show each element and caption when the narration reaches it.
     * argv: text file, voice, rate, mp3 out, words JSON out, proxy (may be empty).
     * Words are written as [{"text", "start", "end"}] in seconds from the start of the clip.
     */
    private static final String SYNTHESIZE_SCRIPT = """
        import asyncio, json, sys
        import edge_tts
        text_file, voice, rate, media_out, words_out, proxy = sys.argv[1:7]
        async def main():
            text = open(text_file, encoding="utf-8").read()
            comm = edge_tts.Communicate(text, voice, rate=rate, boundary="WordBoundary", proxy=proxy or None)
            words = []
            with open(media_out, "wb") as media:
                async for chunk in comm.stream():
                    if chunk["type"] == "audio":
                        media.write(chunk["data"])
                    elif chunk["type"] == "WordBoundary":
                        start = chunk["offset"] / 1e7
                        words.append({"text": chunk["text"], "start": round(start, 3),
                                      "end": round(start + chunk["duration"] / 1e7, 3)})
            with open(words_out, "w", encoding="utf-8") as f:
                json.dump(words, f)
        asyncio.run(main())
        """;
    private static final int TIMEOUT_SECONDS = 60;

    @Override
    public String getName() {
        return "Edge TTS (Microsoft)";
    }

    @Override
    public String getDefaultModel() {
        return DEFAULT_VOICE;
    }

    @Override
    public List<String> getAvailableModels() {
        return AVAILABLE_VOICES;
    }

    @Override
    public VoiceResponse synthesize(VoiceRequest request) {
        long start = System.currentTimeMillis();
        Path tempFile = null;
        Path textFile = null;
        Path wordsFile = null;
        try {
            String text = request.getText();
            if (text == null || text.isBlank()) {
                return VoiceResponse.error("Text is empty");
            }

            String voice = request.getVoice() != null && !request.getVoice().isBlank()
                ? request.getVoice() : DEFAULT_VOICE;

            double speed = request.getSpeed() != null ? request.getSpeed() : 1.0;
            String rate = toEdgeTtsRate(speed);

            tempFile = Files.createTempFile("edge_tts_", ".mp3");
            textFile = Files.createTempFile("edge_tts_text_", ".txt");
            Files.writeString(textFile, text, java.nio.charset.StandardCharsets.UTF_8);

            wordsFile = Files.createTempFile("edge_tts_words_", ".json");
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            // edge-tts (aiohttp) ignores proxy env vars; pass the standard one explicitly.
            String proxy = System.getenv("HTTPS_PROXY") != null ? System.getenv("HTTPS_PROXY") : System.getenv("https_proxy");
            List<String> command = new ArrayList<>(isWindows ? List.of("cmd.exe", "/c", "python") : List.of("python3"));
            command.addAll(List.of("-c", SYNTHESIZE_SCRIPT,
                textFile.toAbsolutePath().toString(), voice, rate,
                tempFile.toAbsolutePath().toString(), wordsFile.toAbsolutePath().toString(),
                proxy != null ? proxy : ""));
            ProcessBuilder pb = new ProcessBuilder(command);
            log.info("Executing Edge TTS: voice={}, rate={}, {} chars", voice, rate, text.length());

            pb.redirectErrorStream(true);
            Process process = pb.start();

            String processOutput = readStream(process.getInputStream());
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return VoiceResponse.error("Edge TTS timed out after " + TIMEOUT_SECONDS + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("Edge TTS exited with code {}: {}", exitCode, processOutput);
                return VoiceResponse.error("Edge TTS failed (exit " + exitCode + "): " + processOutput);
            }

            if (!Files.exists(tempFile) || Files.size(tempFile) == 0) {
                return VoiceResponse.error("Edge TTS produced no audio output");
            }

            byte[] audioBytes = Files.readAllBytes(tempFile);
            long latency = System.currentTimeMillis() - start;

            String dataUri = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(audioBytes);
            int durationSec = estimateDuration(text, speed);

            VoiceResponse response = VoiceResponse.ok(dataUri, voice, latency, durationSec);
            String words = Files.exists(wordsFile) ? Files.readString(wordsFile) : "";
            if (!words.isBlank() && !"[]".equals(words.trim())) response.setWordTimingsJson(words);
            return response;

        } catch (Exception e) {
            log.error("Edge TTS synthesis failed", e);
            return VoiceResponse.error("Edge TTS failed: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
            if (wordsFile != null) {
                try { Files.deleteIfExists(wordsFile); } catch (IOException ignored) {}
            }
            if (textFile != null) {
                try { Files.deleteIfExists(textFile); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public boolean testConnection() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                EDGE_TTS_CMD, EDGE_TTS_MODULE, EDGE_TTS_MODULE_NAME,
                "--text", "test",
                "--voice", DEFAULT_VOICE,
                "--write-media", "NUL"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("Edge TTS connection test failed", e);
            return false;
        }
    }

    private String toEdgeTtsRate(double speed) {
        if (speed <= 0) return "+0%";
        int percent = (int) Math.round((speed - 1.0) * 100);
        if (percent >= 0) return "+" + percent + "%";
        return percent + "%";
    }

    private int estimateDuration(String text, double speed) {
        if (text == null || text.isBlank()) return 0;
        int wordCount = text.split("\\s+").length;
        return (int) Math.ceil((wordCount / 150.0) / speed * 60);
    }

    private String readStream(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }
}
