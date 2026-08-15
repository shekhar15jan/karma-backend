package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

/**
 * Renders a narration-synced MP4 via the karma-renderer Remotion engine
 * (POST {renderer}/video). Each scene carries its own visual_spec JSON and a
 * per-scene audio data URI, so cuts are frame-accurate to the narration.
 * The output MP4 is written to the local media directory and surfaced as a
 * {@code file:} URI, matching {@link FFmpegVideoProvider}.
 */
public class RemotionVideoProvider implements VideoProvider {

    private static final Logger log = LoggerFactory.getLogger(RemotionVideoProvider.class);
    private static final Path DEFAULT_DIR = Paths.get(System.getProperty("user.home"), ".karma", "media");
    private static final String DEFAULT_URL = "http://localhost:3000";

    private final String rendererUrl;
    private final Path outputDir;
    private final ObjectMapper objectMapper;

    public RemotionVideoProvider(String rendererUrl, ObjectMapper objectMapper) {
        this(rendererUrl, DEFAULT_DIR, objectMapper);
    }

    public RemotionVideoProvider(String rendererUrl, Path outputDir, ObjectMapper objectMapper) {
        this.rendererUrl = rendererUrl != null && !rendererUrl.isBlank() ? rendererUrl : DEFAULT_URL;
        this.outputDir = outputDir != null ? outputDir : DEFAULT_DIR;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() { return "Remotion (Local)"; }

    @Override
    public String getDefaultModel() { return "remotion-local"; }

    @Override
    public List<String> getAvailableModels() { return List.of("remotion-local"); }

    @Override
    public VideoResponse generate(VideoRequest request) {
        long start = System.currentTimeMillis();
        try {
            Files.createDirectories(outputDir);
            ObjectNode body = buildPayload(request);
            byte[] mp4 = postForMp4(body);
            if (mp4 == null || mp4.length == 0) {
                return VideoResponse.error("Remotion renderer returned an empty video");
            }
            Path file = outputDir.resolve("video-" + System.currentTimeMillis() + "-" + Math.abs(body.hashCode()) + ".mp4");
            Files.write(file, mp4);
            int duration = probeDurationSeconds(file);
            log.info("Rendered Remotion video to {} ({} seconds)", file, duration);
            return VideoResponse.ok(file.toUri().toString(), duration);
        } catch (Exception e) {
            log.error("Remotion video render failed", e);
            return VideoResponse.error("Remotion video render failed: " + e.getMessage());
        }
    }

    private ObjectNode buildPayload(VideoRequest request) {
        String[] res = parseResolution(request.getResolution());
        int fps = request.getFps() > 0 ? request.getFps() : 30;
        int transitionFrames = (int) Math.round(request.getTransitionDuration() * fps);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("fps", fps);
        ObjectNode resolution = objectMapper.createObjectNode();
        resolution.put("width", Integer.parseInt(res[0]));
        resolution.put("height", Integer.parseInt(res[1]));
        body.set("resolution", resolution);
        body.put("transitionDuration", Math.max(0, transitionFrames));
        body.put("enableIntro", true);
        if (request.getIntroTitle() != null && !request.getIntroTitle().isBlank()) {
            body.put("introTitle", request.getIntroTitle());
        }
        if (request.getIntroSubtitle() != null && !request.getIntroSubtitle().isBlank()) {
            body.put("introSubtitle", request.getIntroSubtitle());
        }
        if (request.getMusicUrl() != null && !request.getMusicUrl().isBlank()) {
            String music = toDataUri(request.getMusicUrl());
            if (music != null) {
                body.put("music", music);
            }
        }

        // Captions config
        if (request.getCaptions() != null) {
            ObjectNode captionsNode = objectMapper.createObjectNode();
            captionsNode.put("burnIn", request.getCaptions().isBurnIn());
            captionsNode.put("preset", request.getCaptions().getPreset() != null ? request.getCaptions().getPreset() : "youtube");
            captionsNode.put("position", request.getCaptions().getPosition());
            captionsNode.put("fontSize", request.getCaptions().getFontSize());
            captionsNode.put("maxCharsPerLine", request.getCaptions().getMaxCharsPerLine());
            body.set("captions", captionsNode);
        }

        // Chapters
        if (request.getChapters() != null && !request.getChapters().isEmpty()) {
            ArrayNode chaptersArray = objectMapper.createArrayNode();
            for (VideoChapter ch : request.getChapters()) {
                ObjectNode chNode = objectMapper.createObjectNode();
                chNode.put("title", ch.getTitle());
                chNode.put("startTime", ch.getStartTime());
                chaptersArray.add(chNode);
            }
            body.set("chapters", chaptersArray);
        }

        ArrayNode scenes = objectMapper.createArrayNode();
        List<VideoScene> sceneList = request.getScenes() != null ? request.getScenes() : List.of();
        for (VideoScene scene : sceneList) {
            ObjectNode sceneNode = objectMapper.createObjectNode();
            ObjectNode visualSpec = parseSpec(scene.getSpec());
            if (visualSpec == null && scene.getImageUrl() != null && !scene.getImageUrl().isBlank()) {
                visualSpec = fallbackSpecFromImage(scene.getImageUrl());
            }
            if (visualSpec != null) {
                BrandingSupport.applyBranding(visualSpec, objectMapper);
                sceneNode.set("visualSpec", visualSpec);
            } else {
                sceneNode.set("visualSpec", emptySpec());
            }
            if (scene.getSsmlAudioMarkers() != null && !scene.getSsmlAudioMarkers().isBlank()) {
                try { sceneNode.set("ssmlAudioMarkers", objectMapper.readTree(scene.getSsmlAudioMarkers())); } catch (Exception e) {}
            }
            if (scene.getVisualTriggerCues() != null && !scene.getVisualTriggerCues().isBlank()) {
                try { sceneNode.set("visualTriggerCues", objectMapper.readTree(scene.getVisualTriggerCues())); } catch (Exception e) {}
            }
            if (scene.getTimelineEvents() != null && !scene.getTimelineEvents().isBlank()) {
                try { sceneNode.set("timelineEvents", objectMapper.readTree(scene.getTimelineEvents())); } catch (Exception e) {}
            }
            if (scene.getAudioUrl() != null && !scene.getAudioUrl().isBlank()) {
                String audio = toDataUri(scene.getAudioUrl());
                if (audio != null) {
                    sceneNode.put("audio", audio);
                }
            }
            // Per-scene captions
            if (scene.getCaptions() != null && !scene.getCaptions().isBlank()) {
                try { sceneNode.set("captions", objectMapper.readTree(scene.getCaptions())); } catch (Exception e) {}
            }
            scenes.add(sceneNode);
        }
        body.set("scenes", scenes);
        return body;
    }

    /**
     * Converts an audio reference to a base64 data URI for the renderer.
     * Accepts local {@code file:} URIs, absolute paths, and already-encoded
     * data URIs; plain http(s) URLs are passed through unchanged.
     */
    private String toDataUri(String reference) {
        if (reference == null || reference.isBlank()) return null;
        if (reference.startsWith("data:")) return reference;
        if (reference.startsWith("http://") || reference.startsWith("https://")) return reference;

        Path path = null;
        if (reference.startsWith("file:")) {
            try {
                path = Paths.get(URI.create(reference));
            } catch (Exception e) {
                log.warn("Invalid file URI for audio: {}", reference);
                return null;
            }
        } else {
            path = Paths.get(reference);
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            String b64 = Base64.getEncoder().encodeToString(bytes);
            String name = path.getFileName().toString().toLowerCase();
            String mime = name.endsWith(".wav") ? "audio/wav"
                    : name.endsWith(".aac") ? "audio/aac"
                    : name.endsWith(".ogg") ? "audio/ogg"
                    : name.endsWith(".mp4") || name.endsWith(".m4a") ? "audio/mp4"
                    : "audio/mpeg";
            return "data:" + mime + ";base64," + b64;
        } catch (Exception e) {
            log.warn("Could not read audio file {}: {}", path, e.getMessage());
            return null;
        }
    }

    private ObjectNode emptySpec() {
        ObjectNode spec = objectMapper.createObjectNode();
        spec.put("layout", "cards");
        spec.put("theme", "whiteboard");
        spec.set("components", objectMapper.createArrayNode());
        spec.set("connections", objectMapper.createArrayNode());
        spec.set("containers", objectMapper.createArrayNode());
        return spec;
    }

    private ObjectNode fallbackSpecFromImage(String imageUrl) {
        ObjectNode spec = emptySpec();
        ObjectNode banner = objectMapper.createObjectNode();
        banner.put("type", "info-card");
        banner.put("label", "Scene visual not provided");
        spec.withArray("components").add(banner);
        return spec;
    }

    private ObjectNode parseSpec(String specJson) {
        if (specJson == null || specJson.isBlank()) return null;
        try {
            return (ObjectNode) objectMapper.readTree(specJson);
        } catch (Exception e) {
            log.warn("Scene visual_spec is not valid JSON: {}", e.getMessage());
            return null;
        }
    }

    private String[] parseResolution(String resolution) {
        if (resolution != null && resolution.matches("\\d+x\\d+")) {
            return resolution.split("x");
        }
        return new String[]{"1920", "1080"};
    }

    private byte[] postForMp4(ObjectNode body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                new URI(rendererUrl.replaceAll("/+$", "") + "/video").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "video/mp4");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(3600000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(objectMapper.writeValueAsBytes(body));
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            String err = readBody(code >= 400 ? conn.getErrorStream() : conn.getInputStream(), 4096);
            throw new IllegalStateException("Remotion renderer returned HTTP " + code + ": " + err);
        }
        try (var is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private static String readBody(java.io.InputStream is, int max) {
        if (is == null) return "";
        try {
            byte[] b = is.readAllBytes();
            return new String(b, 0, Math.min(b.length, max), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private int probeDurationSeconds(Path file) {
        try {
            String[] lines = {
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.toAbsolutePath().toString()
            };
            ProcessBuilder pb = new ProcessBuilder(lines);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0 && !out.isBlank()) {
                return (int) Math.round(Double.parseDouble(out));
            }
        } catch (Exception e) {
            log.warn("Could not probe video duration: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public boolean testConnection() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URI(rendererUrl.replaceAll("/+$", "") + "/health").toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            conn.getInputStream().close();
            return code == 200;
        } catch (Exception e) {
            log.warn("Remotion renderer health check failed: {}", e.getMessage());
            return false;
        }
    }
}
