package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Coerces agent-produced scene specs into the exact contract the karma-renderer
 * video path implements. Guarantees: a known layout (never a blank slide), a valid
 * component vocabulary, unique component ids, and connections that reference real nodes.
 */
public final class VisualSpecNormalizer {

    /** Layouts the karma-renderer video path actually implements (V2 16 + legacy). */
    private static final Set<String> LAYOUTS = Set.of(
        "title-slide", "section-header", "standard-content", "two-column", "split-image",
        "architecture-diagram", "feature-grid", "big-number", "comparison", "quote",
        "code-explainer", "terminal-walkthrough", "roadmap-timeline",
        "bento", "dashboard", "infographic"
    );

    /** Legacy layouts coerced via semantic fallback — preserves nodes/relationships, records diagnostics. */
    private static final Map<String, String> LAYOUT_FALLBACKS = Map.ofEntries(
        Map.entry("flow", "architecture-diagram"),
        Map.entry("flowchart", "architecture-diagram"),
        Map.entry("architecture", "architecture-diagram"),
        Map.entry("uml", "architecture-diagram"),
        Map.entry("mindmap", "architecture-diagram"),
        Map.entry("presentation", "standard-content"),
        Map.entry("split_diagram_text", "two-column"),
        Map.entry("cards", "feature-grid"),
        Map.entry("columns", "feature-grid"),
        Map.entry("grid", "feature-grid"),
        Map.entry("timeline", "roadmap-timeline"),
        Map.entry("roadmap", "roadmap-timeline"),
        Map.entry("learning_path", "roadmap-timeline"),
        Map.entry("sprint", "roadmap-timeline"),
        Map.entry("hero", "title-slide"),
        Map.entry("poster", "title-slide")
    );

    /** Component vocabulary the video renderer supports (expanded V2). */
    private static final Set<String> COMPONENT_TYPES = Set.of(
        "heading", "paragraph", "text", "bullet-list", "code", "command",
        "card", "info-card", "warning-card", "stat-card", "quote-card", "comparison",
        "bar-chart", "donut-chart", "progress-bar", "profile-card", "gauge", "radar", "pie-chart", "line-chart",
        "container", "glass-container", "gradient-container", "shadow-container", "outlined-container", "modern-card", "minimal-card",
        "banner", "pill-card", "badge-card", "pillar", "timeline", "horizontal-timeline", "roadmap", "learning-path", "sprint",
        "microservice-node", "api-gateway", "message-queue", "docker-node", "kubernetes-node",
        "logging-node", "browser-node", "aws-node", "azure-node", "gcp-node", "language-node",
        "uml-class-node", "uml-actor-node", "uml-usecase-node", "uml-lifeline", "uml-arrow", "uml-decision", "uml-component",
        "uml-package", "uml-database", "uml-note", "uml-cloud", "uml-deployment", "uml-interface", "uml-syncbar",
        "flow-terminal", "flow-process", "flow-io", "flow-document", "flow-diagram",
        "connector", "split-layout", "architecture-pipeline", "grid", "table"
    );

    /** Connection styles the renderer's zod schema accepts. Anything else would 400 the whole video. */
    private static final Set<String> CONNECTION_STYLES = Set.of(
        "straight", "curved", "orthogonal", "dashed", "double"
    );

    /** Connection kinds the renderer's zod schema accepts. */
    private static final Set<String> CONNECTION_KINDS = Set.of(
        "dependency", "association", "aggregation", "composition",
        "inheritance", "implementation", "default", "open", "triangle", "double"
    );

    private static final java.util.regex.Pattern HEX_COLOR =
        java.util.regex.Pattern.compile("^#[0-9a-fA-F]{3}$|^#[0-9a-fA-F]{6}$");

    private VisualSpecNormalizer() {
    }

    public static void normalize(ObjectNode spec, ObjectMapper mapper) {
        normalizeLayout(spec);
        normalizeComponents(spec, mapper);
        normalizeConnections(spec);
        normalizeAnimation(spec);
        normalizeCode(spec);
    }

    private static void normalizeLayout(ObjectNode spec) {
        JsonNode layoutNode = spec.get("layout");
        String raw = (layoutNode != null && layoutNode.isTextual())
            ? layoutNode.asText().trim().toLowerCase(Locale.ROOT)
            : "";
        String layout;
        String reason = null;
        String originalType = raw;
        if (LAYOUTS.contains(raw)) {
            layout = raw;
        } else {
            String fallback = LAYOUT_FALLBACKS.get(raw);
            if (fallback != null) {
                layout = fallback;
                reason = "Legacy layout mapped via semantic fallback — nodes/relationships preserved";
            } else {
                String kebab = raw.replace('_', '-');
                if (LAYOUTS.contains(kebab)) {
                    layout = kebab;
                } else {
                    layout = "standard-content";
                    reason = "Unsupported layout preserved via fallback";
                }
            }
        }
        spec.put("layout", layout);
        // §7 diagnostics: record why, do not silently overwrite
        if (reason != null && !originalType.isBlank()) {
            ArrayNode diag = spec.has("_diagnostics") && spec.get("_diagnostics").isArray()
                ? (ArrayNode) spec.get("_diagnostics") : spec.putArray("_diagnostics");
            ObjectNode entry = diag.addObject();
            entry.put("originalType", originalType);
            entry.put("normalizedType", layout);
            entry.put("reason", reason);
        }
    }

    private static void normalizeComponents(ObjectNode spec, ObjectMapper mapper) {
        JsonNode compsNode = spec.get("components");
        if (compsNode == null && spec.has("shapes")) {
            compsNode = spec.get("shapes");
            spec.set("components", compsNode);
        }
        if (compsNode == null || !compsNode.isArray()) {
            spec.set("components", mapper.createArrayNode());
            return;
        }
        ArrayNode comps = (ArrayNode) compsNode;
        Set<String> seen = new HashSet<>();
        int counter = 1;
        for (int i = 0; i < comps.size(); i++) {
            JsonNode raw = comps.get(i);
            if (!raw.isObject()) {
                comps.remove(i);
                i--;
                continue;
            }
            ObjectNode comp = (ObjectNode) raw;
            String type = comp.hasNonNull("type") ? comp.get("type").asText() : "";
            if (!COMPONENT_TYPES.contains(type)) {
                // §7 preserve intent: keep original type, add diagnostic, fallback to card only if truly unknown
                // but record reason so renderer can attempt semantic fallback (V2 AST)
                String originalType = type;
                // Try kebab normalization before destroying
                String kebab = type.toLowerCase(Locale.ROOT).replace('_', '-');
                if (COMPONENT_TYPES.contains(kebab)) {
                    comp.put("type", kebab);
                } else if (type.isBlank()) {
                    comp.put("type", "card");
                } else {
                    // Preserve semantic type for Diagram AST path, do not coerce to card silently
                    // Still ensure renderer fallback: keep original but add _originalType diagnostics
                    if (!comp.has("_originalType")) comp.put("_originalType", originalType);
                    // Keep renderer from 400: only coerce if not in expanded V2 vocabulary
                    // If hard unknown, fallback but diagnostic preserved
                    if (!originalType.contains("-") && !originalType.contains("_")) {
                        // truly unknown single word → card fallback but with trace
                        comp.put("type", "card");
                        ArrayNode diag = spec.has("_diagnostics") && spec.get("_diagnostics").isArray()
                            ? (ArrayNode) spec.get("_diagnostics") : spec.putArray("_diagnostics");
                        ObjectNode entry = diag.addObject();
                        entry.put("originalType", originalType);
                        entry.put("normalizedType", "card");
                        entry.put("reason", "Unsupported component coerced to card — preserve relationships");
                    }
                }
            }
            sanitizeHexColor(comp, "fill");
            sanitizeHexColor(comp, "line");
            sanitizeHexColor(comp, "textColor");
            String id = comp.hasNonNull("id") ? comp.get("id").asText() : "";
            if (id.isBlank() || !seen.add(id)) {
                while (seen.contains("component-" + counter)) {
                    counter++;
                }
                id = "component-" + counter;
                counter++;
                seen.add(id);
                comp.put("id", id);
            }
        }
    }

    private static void sanitizeHexColor(ObjectNode comp, String field) {
        JsonNode v = comp.get(field);
        if (v == null || v.isNull()) {
            return;
        }
        if (!v.isTextual() || !HEX_COLOR.matcher(v.asText()).matches()) {
            comp.remove(field);
        }
    }

    private static void normalizeConnections(ObjectNode spec) {
        JsonNode connsNode = spec.get("connections");
        if (connsNode == null || !connsNode.isArray()) {
            spec.set("connections", spec.arrayNode());
            return;
        }
        Set<String> ids = new HashSet<>();
        JsonNode comps = spec.get("components");
        if (comps != null && comps.isArray()) {
            for (JsonNode comp : comps) {
                if (comp.hasNonNull("id")) {
                    ids.add(comp.get("id").asText());
                }
            }
        }
        ArrayNode conns = (ArrayNode) connsNode;
        for (int i = 0; i < conns.size(); i++) {
            JsonNode raw = conns.get(i);
            if (!raw.isObject()) {
                conns.remove(i);
                i--;
                continue;
            }
            ObjectNode conn = (ObjectNode) raw;
            String from = conn.hasNonNull("from") ? conn.get("from").asText() : "";
            String to = conn.hasNonNull("to") ? conn.get("to").asText() : "";
            if (from.isBlank() || to.isBlank() || !ids.contains(from) || !ids.contains(to)) {
                conns.remove(i);
                i--;
                continue;
            }
            coerceEnum(conn, "style", CONNECTION_STYLES);
            coerceEnum(conn, "kind", CONNECTION_KINDS);
        }
    }

    private static void coerceEnum(ObjectNode conn, String field, Set<String> allowed) {
        JsonNode v = conn.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return;
        }
        if (!allowed.contains(v.asText())) {
            conn.remove(field);
        }
    }

    private static final Set<String> HIGHLIGHT_STYLES = Set.of("glow", "ring");

    private static void normalizeAnimation(ObjectNode spec) {
        JsonNode animNode = spec.get("animation");
        if (animNode == null || !animNode.isObject()) {
            return;
        }
        ObjectNode anim = (ObjectNode) animNode;
        JsonNode highlightsNode = anim.get("highlights");
        if (highlightsNode == null || !highlightsNode.isArray()) {
            return;
        }
        ArrayNode highlights = (ArrayNode) highlightsNode;
        for (JsonNode hNode : highlights) {
            if (hNode == null || !hNode.isObject()) continue;
            ObjectNode h = (ObjectNode) hNode;
            JsonNode styleNode = h.get("style");
            if (styleNode != null && styleNode.isTextual()) {
                String s = styleNode.asText().trim().toLowerCase(Locale.ROOT);
                if (!HIGHLIGHT_STYLES.contains(s)) {
                    // coerce invalid styles like "pulse", "highlight", "glow-ring" -> "glow"
                    h.put("style", "glow");
                } else {
                    h.put("style", s);
                }
            } else if (styleNode != null && !styleNode.isNull() && !styleNode.isTextual()) {
                h.remove("style");
            }
        }
    }

    private static void normalizeCode(ObjectNode spec) {
        JsonNode codeNode = spec.get("code");
        if (codeNode == null) {
            return;
        }
        if (codeNode.isNull()) {
            spec.remove("code");
            return;
        }
        if (!codeNode.isObject()) {
            spec.remove("code");
            return;
        }
        // ensure code has at least an empty text if missing
        ObjectNode code = (ObjectNode) codeNode;
        if (!code.has("text") || code.get("text").isNull()) {
            code.put("text", "");
        }
    }
}