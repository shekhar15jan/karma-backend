package com.karmaos.visual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

/**
 * VisualDirector — Slice 1 (§8). Translates semantic intent into
 * SemanticSceneSpec extensions. AI decides WHAT, renderer decides HOW (§3 P1/P2).
 * Deterministic post-processing: fills intent/camera/animation from
 * narrative beat when LLM omits them. Keeps v1 compat.
 */
@Service
public class VisualDirectorService {

    private final ObjectMapper om;

    public VisualDirectorService(ObjectMapper objectMapper) {
        this.om = objectMapper;
    }

    /**
     * Feature flag: VIDEO_RENDERING_MODE V1 (legacy) vs V2 (semantic).
     * Checked via RuntimeSettings "video.rendering_mode" — defaults V1 until V2 passes acceptance.
     */
    public static final String KEY_VIDEO_RENDERING_MODE = "video.rendering_mode";

    /**
     * Enrich List<VideoScene> with V2 semantic fields if absent, preserving V1.
     * Called from MediaOrchestrationService.buildRemotionScenesV2 path.
     */
    public void enrichVideoScenes(java.util.List<com.karmaos.media.VideoScene> scenes) {
        if (scenes == null) return;
        for (com.karmaos.media.VideoScene vs : scenes) {
            String specStr = vs.getSpec();
            if (specStr == null || specStr.isBlank()) continue;
            try {
                com.fasterxml.jackson.databind.node.ObjectNode spec = (com.fasterxml.jackson.databind.node.ObjectNode) om.readTree(specStr);
                boolean isV2 = spec.has("schemaVersion") && "v2".equals(spec.path("schemaVersion").asText());
                // If already V2, just ensure version and preserve
                if (isV2) {
                    // ensure semantic fields survive normalization
                    vs.setSpec(spec.toString());
                    continue;
                }
                // For V1, enrich with semantic intent if missing (non-destructive)
                if (!spec.has("schemaVersion")) spec.put("schemaVersion", "v2");
                if (!spec.has("visualIntent") && spec.has("intent")) spec.set("visualIntent", spec.get("intent"));
                if (!spec.has("intent")) {
                    String layout = spec.path("layout").asText("");
                    String intent = switch (layout) {
                        case "architecture-diagram" -> "ARCHITECTURE";
                        case "code-explainer" -> "CODE";
                        case "terminal-walkthrough" -> "EXECUTION";
                        case "roadmap-timeline" -> "PROCESS";
                        default -> "EXPLAIN";
                    };
                    spec.put("intent", intent);
                    spec.put("visualIntent", intent);
                }
                // semanticObjects from components if absent
                if (!spec.has("semanticObjects") && spec.has("components") && spec.get("components").isArray()) {
                    com.fasterxml.jackson.databind.node.ArrayNode comps = (com.fasterxml.jackson.databind.node.ArrayNode) spec.get("components");
                    com.fasterxml.jackson.databind.node.ArrayNode objs = om.createArrayNode();
                    for (com.fasterxml.jackson.databind.JsonNode c : comps) {
                        if (!c.has("id")) continue;
                        com.fasterxml.jackson.databind.node.ObjectNode o = om.createObjectNode();
                        o.put("id", c.path("id").asText());
                        String type = c.path("type").asText("SERVICE");
                        // map widget type to semantic type
                        String semType = switch (type) {
                            case "api-gateway" -> "API_GATEWAY";
                            case "message-queue" -> "KAFKA";
                            case "microservice-node" -> "SERVICE";
                            case "browser-node" -> "MOBILE_APP";
                            default -> type.toUpperCase().replace('-', '_');
                        };
                        o.put("type", semType);
                        o.put("label", c.path("label").asText(""));
                        objs.add(o);
                    }
                    if (objs.size() > 0) spec.set("semanticObjects", objs);
                }
                // semanticAnimations from connections -> TRAVEL if absent
                if (!spec.has("semanticAnimations") && spec.has("connections") && spec.get("connections").isArray()) {
                    com.fasterxml.jackson.databind.node.ArrayNode conns = (com.fasterxml.jackson.databind.node.ArrayNode) spec.get("connections");
                    if (conns.size() > 0) {
                        com.fasterxml.jackson.databind.node.ArrayNode anims = om.createArrayNode();
                        for (com.fasterxml.jackson.databind.JsonNode conn : conns) {
                            com.fasterxml.jackson.databind.node.ObjectNode a = om.createObjectNode();
                            a.put("type", "TRAVEL");
                            a.put("object", "payment-request");
                            a.put("from", conn.path("from").asText());
                            a.put("to", conn.path("to").asText());
                            a.put("at", 1.0);
                            a.put("duration", 1.2);
                            anims.add(a);
                        }
                        spec.set("semanticAnimations", anims);
                    }
                }
                // camera FOLLOW for ARCHITECTURE
                if (!spec.has("camera") && "ARCHITECTURE".equals(spec.path("intent").asText())) {
                    com.fasterxml.jackson.databind.node.ObjectNode cam = om.createObjectNode();
                    cam.put("mode", "FOLLOW");
                    cam.put("target", "payment-request");
                    spec.set("camera", cam);
                }
                // Also support legacy visual_spec_v2 container: if scene has top-level visual_spec_v2, merge
                vs.setSpec(spec.toString());
            } catch (Exception ignored) {}
        }
    }

    /**
     * Enrich each scene in script_body with semantic defaults:
     * - intent from layout/purpose heuristic
     * - semanticObjects from components (type inference)
     * - TRAVEL animation from connections
     * - FOLLOW camera when ARCHITECTURE/SIMULATION
     */
    public JsonNode direct(JsonNode scriptBody) {
        if (!scriptBody.isObject() || !scriptBody.has("script_body")) return scriptBody;
        ArrayNode scenes = (ArrayNode) scriptBody.get("script_body");
        for (JsonNode scene : scenes) {
            ObjectNode vs = (ObjectNode) scene.path("visual_spec");
            if (vs.isMissingNode() || vs.isNull()) continue;
            // intent heuristic
            if (!vs.has("intent")) {
                String layout = vs.path("layout").asText("");
                String intent = switch (layout) {
                    case "architecture-diagram" -> "ARCHITECTURE";
                    case "code-explainer" -> "CODE";
                    case "terminal-walkthrough" -> "EXECUTION";
                    case "roadmap-timeline" -> "PROCESS";
                    default -> "EXPLAIN";
                };
                vs.put("intent", intent);
            }
            // semanticAnimations from connections -> TRAVEL
            if (!vs.has("semanticAnimations") && vs.has("connections")) {
                ArrayNode conns = (ArrayNode) vs.get("connections");
                if (conns.size() > 0) {
                    ArrayNode anims = om.createArrayNode();
                    for (JsonNode c : conns) {
                        ObjectNode a = om.createObjectNode();
                        a.put("type", "travel");
                        a.put("from", c.path("from").asText());
                        a.put("to", c.path("to").asText());
                        a.put("at", 1.0);
                        a.put("duration", 1.2);
                        anims.add(a);
                    }
                    vs.set("semanticAnimations", anims);
                }
            }
            // camera for architecture
            if (!vs.has("camera") && "ARCHITECTURE".equals(vs.path("intent").asText())) {
                ObjectNode cam = om.createObjectNode();
                cam.put("mode", "FOLLOW");
                vs.set("camera", cam);
                vs.put("assetClass", "DETERMINISTIC");
            }
        }
        return scriptBody;
    }
}
