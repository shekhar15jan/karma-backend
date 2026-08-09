package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders scene images via the karma-renderer visual engine (Node + Puppeteer,
 * component-based HTML/SVG rendering). The spec is the raw scene JSON; when it
 * carries the legacy {@code shapes}/{@code arrows} form it is translated to the
 * component/connection form the renderer understands. Output is written to the
 * same {@code ~/.karma/media} directory and surfaced as a local {@code file:}
 * URI, exactly like {@link DiagramImageProvider}.
 */
public class VisualRenderImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(VisualRenderImageProvider.class);
    private static final Path DEFAULT_DIR = Paths.get(System.getProperty("user.home"), ".karma", "media");
    private static final String DEFAULT_URL = "http://localhost:3000";

    private final String rendererUrl;
    private final Path outputDir;
    private final ObjectMapper objectMapper;

    public VisualRenderImageProvider() {
        this(System.getenv("RENDERER_URL") != null ? System.getenv("RENDERER_URL") : DEFAULT_URL, DEFAULT_DIR, new ObjectMapper());
    }

    public VisualRenderImageProvider(String rendererUrl, Path outputDir, ObjectMapper objectMapper) {
        this.rendererUrl = rendererUrl != null && !rendererUrl.isBlank() ? rendererUrl : DEFAULT_URL;
        this.outputDir = outputDir != null ? outputDir : DEFAULT_DIR;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() { return "visual-engine"; }

    @Override
    public String getDefaultModel() { return "karma-renderer"; }

    @Override
    public List<String> getAvailableModels() { return List.of("karma-renderer"); }

    @Override
    public ImageResponse generate(ImageRequest request) {
        long start = System.currentTimeMillis();
        try {
            Files.createDirectories(outputDir);
            String prompt = request.getPrompt() != null ? request.getPrompt().trim() : "";
            if (prompt.isBlank()) {
                return ImageResponse.error("Visual engine requires a scene spec JSON prompt");
            }
            JsonNode spec = parseSpec(prompt);
            if (spec == null) {
                return ImageResponse.error("Visual engine requires a valid scene spec JSON prompt");
            }
            ObjectNode visualSpec = toVisualSpec(spec);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("format", "png");
            body.set("spec", visualSpec);
            byte[] png = postForPng(body);
            if (png == null || png.length == 0) {
                return ImageResponse.error("Visual engine returned an empty image");
            }
            Path file = outputDir.resolve("visual-" + System.currentTimeMillis() + "-" + Math.abs(visualSpec.hashCode()) + ".png");
            Files.write(file, png);
            log.info("Rendered visual scene to {}", file);
            return ImageResponse.ok(List.of(file.toUri().toString()), prompt, getDefaultModel(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Visual render failed", e);
            return ImageResponse.error("Visual render failed: " + e.getMessage());
        }
    }

    /**
     * Translates the scene spec into the renderer's visual_spec shape.
     * Legacy diagram_specs ({@code shapes}/{@code arrows}) are converted;
     * specs already using {@code components}/{@code connections} pass through.
     */
    private ObjectNode toVisualSpec(JsonNode spec) {
        ObjectNode out = objectMapper.createObjectNode();
        if (spec.isObject() && spec.has("components")) {
            out.setAll((ObjectNode) spec);
            return out;
        }
        String theme = spec.path("theme").asText("whiteboard");
        String layout = spec.path("layout").asText("flow");
        out.put("theme", theme);
        out.put("layout", layout);
        if (spec.has("title")) out.put("title", spec.get("title").asText());
        if (spec.has("instructions")) out.set("instructions", spec.get("instructions"));
        if (spec.has("code")) out.set("code", spec.get("code"));

        ArrayNode components = objectMapper.createArrayNode();
        if (spec.has("shapes") && spec.get("shapes").isArray()) {
            for (JsonNode shape : spec.get("shapes")) {
                ObjectNode comp = objectMapper.createObjectNode();
                comp.put("id", shape.path("id").asText("s" + components.size()));
                String type = shape.path("type").asText("box");
                comp.put("type", normalizeShapeType(type));
                if (shape.has("label")) comp.put("label", shape.get("label").asText());
                if (shape.has("fill")) comp.put("fill", shape.get("fill").asText());
                if (shape.has("line")) comp.put("line", shape.get("line").asText());
                if (shape.has("icon")) comp.put("icon", shape.get("icon").asText());
                components.add(comp);
            }
        }
        out.set("components", components);

        ArrayNode connections = objectMapper.createArrayNode();
        if (spec.has("arrows") && spec.get("arrows").isArray()) {
            for (JsonNode arrow : spec.get("arrows")) {
                ObjectNode conn = objectMapper.createObjectNode();
                if (arrow.has("from")) conn.put("from", arrow.get("from").asText());
                if (arrow.has("to")) conn.put("to", arrow.get("to").asText());
                if (arrow.has("label")) conn.put("label", arrow.get("label").asText());
                if (arrow.has("style")) conn.put("style", arrow.get("style").asText());
                if (arrow.has("kind")) conn.put("kind", arrow.get("kind").asText());
                connections.add(conn);
            }
        }
        out.set("connections", connections);
        return out;
    }

    private static String normalizeShapeType(String type) {
        return switch (type == null ? "box" : type.toLowerCase()) {
            case "rect", "rectangle" -> "box";
            case "ellipse", "circle", "start", "end" -> "oval";
            case "decision" -> "diamond";
            case "input", "output" -> "parallelogram";
            case "database", "storage" -> "cylinder";
            case "process" -> "hexagon";
            default -> type.toLowerCase();
        };
    }

    private JsonNode parseSpec(String prompt) {
        try {
            return objectMapper.readTree(prompt);
        } catch (Exception e) {
            log.warn("Scene spec is not valid JSON: {}", e.getMessage());
            return null;
        }
    }

    private byte[] postForPng(ObjectNode body) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL(rendererUrl.replaceAll("/+$", "") + "/render").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "image/png");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(objectMapper.writeValueAsBytes(body));
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            String err = readBody(code >= 400 ? conn.getErrorStream() : conn.getInputStream(), 4096);
            throw new IllegalStateException("Renderer returned HTTP " + code + ": " + err);
        }
        try (java.io.InputStream is = conn.getInputStream()) {
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

    @Override
    public boolean testConnection() {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(rendererUrl.replaceAll("/+$", "") + "/health").openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            conn.getInputStream().close();
            return code == 200;
        } catch (Exception e) {
            log.warn("Renderer health check failed: {}", e.getMessage());
            return false;
        }
    }
}
