package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.agent.Agent;
import com.karmaos.agent.AgentRepository;
import com.karmaos.agent.AgentRuntimeService;
import com.karmaos.llm.LLMResponse;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fills in visuals for video scenes whose script section carried no usable {@code visual_spec}.
 * Without this, such scenes rendered as an empty whiteboard slide or a "Scene visual not provided"
 * card. Specs are drafted by the LLM in one batch and sanitized against layouts and component
 * shapes the renderer is known to draw well; when no LLM is available a clean bullet slide built
 * from the narration is used instead.
 */
@Service
public class SceneVisualGenerator {

    private static final Logger log = LoggerFactory.getLogger(SceneVisualGenerator.class);

    static final String THEME = "codeatcloud";
    private static final int MAX_COMPONENTS = 8;
    private static final Pattern HEX_COLOR = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    private static final Set<String> LAYOUTS = Set.of(
            "title-slide", "section-header", "standard-content", "two-column", "comparison",
            "code-explainer", "terminal-walkthrough", "roadmap-timeline", "architecture-diagram",
            "feature-grid", "big-number", "quote");

    private static final Set<String> COMPONENT_TYPES = Set.of(
            "heading", "paragraph", "bullet-list", "code", "card", "badge",
            "microservice-node", "api-gateway", "message-queue", "database", "docker-node",
            "kubernetes-node", "logging-node", "browser-node", "aws-node", "azure-node", "gcp-node",
            "language-node", "flow-process", "flow-terminal", "flow-io", "flow-document",
            "uml-class-node", "uml-actor-node", "command", "output");

    private static final Set<String> TONES = Set.of("slate", "indigo", "emerald", "rose", "amber");

    private static final String SYSTEM_PROMPT = """
            You are the visual designer for a top technical YouTube channel (Fireship, ByteByteGo style).
            For each numbered scene you get the narration; design ONE on-screen visual that shows what
            the narrator is saying. Viewers must understand the idea from the visual alone.

            Return ONLY a JSON object (no markdown fences, no commentary):
            {"scenes": [{"index": 0, "visual": {"layout": "...", "title": "...", "subtitle": "...", "components": [...]}}]}

            RULES: short on-screen text (max ~8 words per label, max 6 bullets); never the same layout
            twice in a row; prefer diagrams, code and terminals over plain bullets whenever the narration
            describes a system, an API, a command or code. Code max 12 lines, real and runnable.

            ALLOWED LAYOUTS and the components each one uses:
            - "title-slide": heading (label), paragraph (data.body), badge (label)
            - "section-header": title + subtitle, optional paragraph (data.body)
            - "standard-content": heading, paragraph (data.body), bullet-list (data.items: [strings]), card
            - "two-column" / "comparison": left = heading/paragraph, right = cards, bullet-list or code
            - "code-explainer": title, subtitle, bullet-list explaining the code, ONE code component (data.lang, data.text)
            - "terminal-walkthrough": components of type "command" (label = shell command) and "output" (label = its output)
            - "roadmap-timeline": 3-5 components with a label each (ordered steps)
            - "architecture-diagram": 3-5 nodes in data-flow order, chosen from: microservice-node (label, data.stack: ["java","node",...]),
              api-gateway, message-queue, database, docker-node, kubernetes-node (data.pods), logging-node, browser-node,
              aws-node / azure-node / gcp-node (data.service), language-node (data.language), flow-process
            - "feature-grid": 2-6 cards (label, sublabel, optional data.tone: slate|indigo|emerald|rose|amber)
            - "big-number": first component is a card with data.value (e.g. "10x", "99.9%"), second is a paragraph (data.body)
            - "quote": one paragraph (data.body)

            Component shape: {"type": "...", "label": "...", "sublabel": "...", "data": {...}}.
            """;

    private final AgentRuntimeService agentRuntimeService;
    private final AgentRepository agentRepository;
    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;

    public SceneVisualGenerator(AgentRuntimeService agentRuntimeService, AgentRepository agentRepository,
                                ProviderRepository providerRepository, ObjectMapper objectMapper) {
        this.agentRuntimeService = agentRuntimeService;
        this.agentRepository = agentRepository;
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
    }

    /** Sets a visual spec on every scene that has none. Returns how many scenes were filled. */
    public int fillMissingVisuals(List<VideoScene> scenes, UUID agentId, UUID missionId, String topic) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < scenes.size(); i++) {
            String spec = scenes.get(i).getSpec();
            if (spec == null || spec.isBlank()) missing.add(i);
        }
        if (missing.isEmpty()) return 0;

        Map<Integer, Map<String, Object>> drafted = draftWithLlm(scenes, missing, agentId, missionId, topic);
        int fromLlm = 0;
        for (int index : missing) {
            Map<String, Object> visual = drafted.get(index);
            if (visual != null) {
                fromLlm++;
            } else {
                visual = fallbackVisual(scenes.get(index).getText(), topic);
            }
            try {
                scenes.get(index).setSpec(objectMapper.writeValueAsString(visual));
                // The spec replaces the image fallback that rendered as "Scene visual not provided".
                scenes.get(index).setImageUrl(null);
            } catch (Exception e) {
                log.warn("Could not serialize generated visual for scene {}: {}", index, e.getMessage());
            }
        }
        log.info("Filled {} scene(s) missing a visual_spec ({} drafted by LLM, {} narration fallback)",
                missing.size(), fromLlm, missing.size() - fromLlm);
        return missing.size();
    }

    private Map<Integer, Map<String, Object>> draftWithLlm(List<VideoScene> scenes, List<Integer> missing,
                                                           UUID agentId, UUID missionId, String topic) {
        StringBuilder user = new StringBuilder();
        user.append("Video topic: ").append(topic == null || topic.isBlank() ? "Technical explainer" : topic).append("\n\n");
        for (int index : missing) {
            String text = scenes.get(index).getText();
            user.append("Scene ").append(index).append(": ")
                    .append(text == null ? "" : text.length() > 900 ? text.substring(0, 900) : text).append("\n\n");
        }
        try {
            Agent agent = agentId != null ? agentRepository.findById(agentId).orElse(null) : null;
            Provider provider = agent != null && agent.getDefaultProviderId() != null
                    ? providerRepository.findById(agent.getDefaultProviderId()).orElse(null) : null;
            LLMResponse response = agentRuntimeService.executeWithRetryAndFallback(agent, provider, null,
                    SYSTEM_PROMPT, user.toString(), missionId, 8000,
                    content -> !parse(content, missing).isEmpty(), false);
            if (response.isSuccess()) {
                return parse(response.getContent(), missing);
            }
            log.warn("Scene visual drafting failed: {}", response.getErrorMessage());
        } catch (Exception e) {
            log.warn("Scene visual drafting error: {}", e.getMessage());
        }
        return Map.of();
    }

    /** Parses {"scenes":[{"index":n,"visual":{...}}]} into sanitized specs for the requested indices. */
    Map<Integer, Map<String, Object>> parse(String content, List<Integer> wanted) {
        Map<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        JsonNode root = readJson(content);
        if (root == null) return result;
        for (JsonNode node : root.path("scenes")) {
            int index = node.path("index").asInt(-1);
            if (!wanted.contains(index) || result.containsKey(index)) continue;
            Map<String, Object> visual = sanitizeVisual(node.path("visual"));
            if (visual != null) result.put(index, visual);
        }
        return result;
    }

    /** Returns null when the visual has no drawable component. */
    Map<String, Object> sanitizeVisual(JsonNode visual) {
        if (visual == null || !visual.isObject()) return null;
        String layout = visual.path("layout").asText("");
        if (!LAYOUTS.contains(layout)) layout = "standard-content";

        List<Map<String, Object>> components = new ArrayList<>();
        for (JsonNode c : visual.path("components")) {
            Map<String, Object> component = sanitizeComponent(c);
            if (component != null) components.add(component);
            if (components.size() >= MAX_COMPONENTS) break;
        }
        if (components.isEmpty()) return null;

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("layout", layout);
        spec.put("theme", THEME);
        String title = text(visual, "title", 80);
        String subtitle = text(visual, "subtitle", 100);
        if (!title.isBlank()) spec.put("title", title);
        if (!subtitle.isBlank()) spec.put("subtitle", subtitle);
        spec.put("components", components);
        return spec;
    }

    private Map<String, Object> sanitizeComponent(JsonNode c) {
        if (c == null || !c.isObject()) return null;
        String type = c.path("type").asText("");
        if (!COMPONENT_TYPES.contains(type)) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        String id = c.path("id").asText("").replaceAll("[^A-Za-z0-9_-]", "");
        if (!id.isBlank()) out.put("id", id.length() > 40 ? id.substring(0, 40) : id);
        String label = text(c, "label", type.equals("command") || type.equals("output") ? 200 : 80);
        String sublabel = text(c, "sublabel", 140);
        if (!label.isBlank()) out.put("label", label);
        if (!sublabel.isBlank()) out.put("sublabel", sublabel);
        for (String colorKey : List.of("fill", "line", "textColor")) {
            String color = c.path(colorKey).asText("");
            if (HEX_COLOR.matcher(color).matches()) out.put(colorKey, color);
        }

        JsonNode d = c.path("data");
        Map<String, Object> data = new LinkedHashMap<>();
        putText(data, d, "body", 400);
        putText(data, d, "lang", 20);
        putText(data, d, "value", 20);
        putText(data, d, "service", 30);
        putText(data, d, "language", 20);
        if (d.hasNonNull("text")) data.put("text", trimCode(d.path("text").asText("")));
        String tone = d.path("tone").asText("");
        if (TONES.contains(tone)) data.put("tone", tone);
        if (d.path("pods").isInt()) data.put("pods", Math.max(1, Math.min(6, d.path("pods").asInt())));
        List<String> items = stringList(d.path("items"), 6, 90);
        if (items.isEmpty()) items = stringList(c.path("items"), 6, 90);
        if (!items.isEmpty()) data.put("items", items);
        for (String listKey : List.of("stack", "attributes", "methods")) {
            List<String> values = stringList(d.path(listKey), 6, 40);
            if (!values.isEmpty()) data.put(listKey, values);
        }
        if (!data.isEmpty()) out.put("data", data);

        boolean hasContent = out.containsKey("label") || out.containsKey("sublabel") || !data.isEmpty();
        return hasContent ? out : null;
    }

    /** Dark-theme slide with the narration's key sentences; never a placeholder card. */
    Map<String, Object> fallbackVisual(String narration, String topic) {
        List<String> points = keyPoints(narration == null ? "" : narration, 4);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("layout", "standard-content");
        spec.put("theme", THEME);
        spec.put("title", points.isEmpty() ? (topic == null || topic.isBlank() ? "Key idea" : topic) : truncateWords(points.get(0), 7));
        List<String> bullets = points.size() > 1 ? points.subList(1, points.size()) : points;
        if (bullets.isEmpty()) bullets = List.of(topic == null || topic.isBlank() ? "Key idea" : topic);
        spec.put("components", List.of(Map.of("type", "bullet-list", "data", Map.of("items", bullets))));
        return spec;
    }

    private JsonNode readJson(String content) {
        if (content == null) return null;
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return objectMapper.readTree(content.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> keyPoints(String text, int max) {
        String clean = text.replaceAll("[*_#`>]", "").replaceAll("\\s+", " ").trim();
        List<String> points = new ArrayList<>();
        for (String sentence : SENTENCE_END.split(clean)) {
            String s = sentence.trim();
            if (s.length() >= 12) points.add(truncateWords(s, 14));
            if (points.size() >= max) break;
        }
        if (points.isEmpty() && !clean.isBlank()) points.add(truncateWords(clean, 14));
        return points;
    }

    private static String truncateWords(String text, int maxWords) {
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) return text.trim().replaceAll("[.!?]+$", "");
        return String.join(" ", java.util.Arrays.copyOf(words, maxWords)) + "...";
    }

    private static String trimCode(String code) {
        String[] lines = code.replace("\t", "  ").split("\n");
        int max = Math.min(lines.length, 14);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            String line = lines[i].length() > 90 ? lines[i].substring(0, 90) : lines[i];
            sb.append(line).append(i < max - 1 ? "\n" : "");
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String field, int maxLength) {
        String value = node.path(field).isTextual() ? node.path(field).asText().trim() : "";
        return value.length() > maxLength ? value.substring(0, maxLength).trim() : value;
    }

    private static void putText(Map<String, Object> target, JsonNode node, String field, int maxLength) {
        String value = text(node, field, maxLength);
        if (!value.isBlank()) target.put(field, value);
    }

    private static List<String> stringList(JsonNode node, int maxItems, int maxLength) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) return values;
        for (JsonNode item : node) {
            String value = item.isTextual() ? item.asText().trim() : item.path("label").asText("").trim();
            if (value.isBlank()) continue;
            values.add(value.length() > maxLength ? value.substring(0, maxLength).trim() : value);
            if (values.size() >= maxItems) break;
        }
        return values;
    }
}
