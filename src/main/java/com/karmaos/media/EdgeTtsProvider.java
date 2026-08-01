package com.karmaos.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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

    private static final String DEFAULT_VOICE = "en-IN-NeerjaNeural";
    private static final String EDGE_TTS_CMD = "python";
    private static final String EDGE_TTS_MODULE = "-m";
    private static final String EDGE_TTS_MODULE_NAME = "edge_tts";
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

            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder(
                    "cmd.exe", "/c", "python", "-m", "edge_tts",
                    "--voice", voice,
                    "--text", text,
                    "--rate", rate,
                    "--write-media", tempFile.toAbsolutePath().toString()
                );
            } else {
                pb = new ProcessBuilder(
                    "python3", "-m", "edge_tts",
                    "--voice", voice,
                    "--text", text,
                    "--rate", rate,
                    "--write-media", tempFile.toAbsolutePath().toString()
                );
            }

            log.info("Executing Edge TTS command: {}", String.join(" ", pb.command()));

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

            return VoiceResponse.ok(dataUri, voice, latency, durationSec);

        } catch (Exception e) {
            log.error("Edge TTS synthesis failed", e);
            return VoiceResponse.error("Edge TTS failed: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
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
