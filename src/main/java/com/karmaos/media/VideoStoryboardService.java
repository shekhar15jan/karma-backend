package com.karmaos.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.agent.AgentRuntimeService;
import com.karmaos.llm.LLMResponse;
import com.karmaos.mission.Mission;
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
 * Turns a video script into a renderer storyboard: per-scene narration plus a structured visual
 * (architecture diagram, code walkthrough, terminal, feature grid, ...) and YouTube metadata.
 * The LLM drafts it; everything is sanitized against what karma-renderer can actually draw.
 */
@Service
public class VideoStoryboardService {

    private static final Logger log = LoggerFactory.getLogger(VideoStoryboardService.class);

    public static final String THEME = "codeatcloud";
    private static final int MIN_SCENES = 3;
    private static final int MAX_SCENES = 18;
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
            You are a senior producer for a top technical YouTube channel (think Fireship, ByteByteGo, \
            Hussein Nasser). You turn a script into a storyboard for an animated explainer video that \
            hooks viewers in the first 5 seconds and keeps them watching.

            Return ONLY a JSON object (no markdown fences, no commentary) with this exact shape:
            {
              "title": "YouTube title, max 70 chars, curiosity-driven, includes the main keyword",
              "description": "2-3 short paragraphs for the YouTube description: what viewers learn and why it matters",
              "tags": ["8-15 lowercase search tags"],
              "thumbnail_text": "2-4 punchy words for the thumbnail, e.g. 'KAFKA IN 5 MIN'",
              "thumbnail_subtitle": "max 3 words, e.g. 'explained simply'",
              "badges": ["1-3 short tech names shown on the thumbnail, e.g. 'Java', 'Docker'"],
              "scenes": [
                {
                  "chapter": "Short chapter name if this scene starts a new section, else omit",
                  "narration": "What the narrator says during this scene (25-60 words)",
                  "visual": { "layout": "...", "title": "...", "subtitle": "...", "components": [ ... ] }
                }
              ]
            }

            STORY STRUCTURE (8-16 scenes):
            1. Hook (scene 1): a bold claim, surprising number or painful problem. Use "big-number" or "title-slide".
            2. Context: why this matters, what the viewer will be able to do after watching.
            3. Core sections: explain concepts with diagrams, then show real code or commands.
            4. Recap: 3-6 key takeaways ("feature-grid").
            5. Call to action (last scene): ask viewers to like, subscribe and comment with a specific question.

            NARRATION RULES: conversational, second person, energetic but precise. Plain spoken English only:
            no markdown, no emojis, no URLs, no code read aloud character by character. Never say "this slide"
            or "on screen". Each scene's narration must describe what its visual shows.

            VISUAL RULES: every scene needs a visual. Never use the same layout twice in a row. Keep text on
            screen short (max ~8 words per card label, max 6 bullets). Code max 12 lines, real and runnable.

            ALLOWED LAYOUTS and the components each one uses:
            - "title-slide": heading (label), paragraph (data.body), badge (label)
            - "section-header": title + subtitle, optional paragraph (data.body)
            - "standard-content": heading, paragraph (data.body), bullet-list (data.items: [strings]), card
            - "two-column" / "comparison": left = heading/paragraph, right = cards, bullet-list or code
            - "code-explainer": title, subtitle, bullet-list explaining the code, ONE code component (data.lang, data.text)
            - "terminal-walkthrough": components of type "command" (label = the shell command) and "output" (label = its output)
            - "roadmap-timeline": 3-6 components with a label each (ordered steps)
            - "architecture-diagram": 3-5 nodes in data-flow order, chosen from: microservice-node (label, data.stack: ["java","node",...]),
              api-gateway, message-queue, database, docker-node, kubernetes-node (data.pods), logging-node, browser-node,
              aws-node / azure-node / gcp-node (data.service), language-node (data.language), flow-process
            - "feature-grid": 2-6 cards (label, sublabel, optional data.tone: slate|indigo|emerald|rose|amber)
            - "big-number": first component is a card with data.value (e.g. "10x", "99.9%"), second is a paragraph (data.body)
            - "quote": one paragraph (data.body)

            Component shape: {"type": "...", "label": "...", "sublabel": "...", "data": {...}}.
            """;

    private final AgentRuntimeService agentRuntimeService;
    private final ObjectMapper objectMapper;

    public VideoStoryboardService(AgentRuntimeService agentRuntimeService, ObjectMapper objectMapper) {
        this.agentRuntimeService = agentRuntimeService;
        this.objectMapper = objectMapper;
    }

    public Storyboard build(UUID agentId, Mission mission, String script, String reviewerFeedback) {
        String topic = mission != null && mission.getName() != null ? mission.getName() : "Technical deep dive";
        StringBuilder user = new StringBuilder();
        user.append("Video topic: ").append(topic).append("\n");
        if (mission != null && mission.getDescription() != null && !mission.getDescription().isBlank()) {
            user.append("Brief: ").append(mission.getDescription()).append("\n");
        }
        user.append("\nSCRIPT / RESEARCH TO ADAPT:\n").append(script == null || script.isBlank() ? topic : script).append("\n");
        if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
            user.append("\nREVIEWER FEEDBACK ON THE PREVIOUS VIDEO (address it):\n").append(reviewerFeedback).append("\n");
        }

        try {
            LLMResponse response = agentRuntimeService.completeForAgent(agentId, SYSTEM_PROMPT, user.toString(),
                    content -> parse(content, topic) != null);
            if (response.isSuccess()) {
                Storyboard storyboard = parse(response.getContent(), topic);
                if (storyboard != null) {
                    log.info("Storyboard drafted by LLM: {} scenes, title='{}'", storyboard.scenes().size(), storyboard.title());
                    return storyboard;
                }
            }
            log.warn("LLM storyboard unavailable ({}); using script-derived storyboard", response.getErrorMessage());
        } catch (Exception e) {
            log.warn("LLM storyboard failed; using script-derived storyboard: {}", e.getMessage());
        }
        return fallback(topic, script);
    }

    /** Parses and sanitizes LLM output; null when it is not a usable storyboard. */
    Storyboard parse(String content, String topic) {
        JsonNode root = readJson(content);
        if (root == null || !root.isObject()) return null;
        JsonNode scenesNode = root.path("scenes");
        if (!scenesNode.isArray()) return null;

        List<Scene> scenes = new ArrayList<>();
        for (JsonNode sceneNode : scenesNode) {
            String narration = cleanNarration(sceneNode.path("narration").asText(""));
            if (narration.split("\\s+").length < 5) continue;
            Map<String, Object> visual = sanitizeVisual(sceneNode.path("visual"), narration);
            String chapter = text(sceneNode, "chapter", 60);
            scenes.add(new Scene(chapter.isBlank() ? null : chapter, narration, visual));
            if (scenes.size() >= MAX_SCENES) break;
        }
        if (scenes.size() < MIN_SCENES) return null;

        String title = text(root, "title", 100);
        return new Storyboard(
                title.isBlank() ? topic : title,
                text(root, "description", 4000),
                stringList(root.path("tags"), 20, 40),
                orDefault(text(root, "thumbnail_text", 40), shortTitle(topic)),
                text(root, "thumbnail_subtitle", 30),
                stringList(root.path("badges"), 3, 16),
                scenes);
    }

    private Map<String, Object> sanitizeVisual(JsonNode visual, String narration) {
        String layout = visual.path("layout").asText("");
        if (!LAYOUTS.contains(layout)) layout = "standard-content";

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("layout", layout);
        spec.put("theme", THEME);
        String title = text(visual, "title", 80);
        String subtitle = text(visual, "subtitle", 100);
        if (!title.isBlank()) spec.put("title", title);
        if (!subtitle.isBlank()) spec.put("subtitle", subtitle);

        List<Map<String, Object>> components = new ArrayList<>();
        for (JsonNode c : visual.path("components")) {
            Map<String, Object> component = sanitizeComponent(c);
            if (component != null) components.add(component);
            if (components.size() >= MAX_COMPONENTS) break;
        }
        if (components.isEmpty()) {
            // Never render an empty frame: show the scene's key sentences instead.
            spec.put("layout", "standard-content");
            components.add(Map.of("type", "bullet-list", "data", Map.of("items", keyPoints(narration, 3))));
        }
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

    /** Script-derived storyboard used when no LLM is available or its output is unusable. */
    Storyboard fallback(String topic, String script) {
        List<String> sections = new ArrayList<>();
        JsonNode root = readJson(script);
        if (root != null && root.path("script_body").isArray()) {
            for (JsonNode section : root.path("script_body")) {
                String text = cleanNarration(section.path("speaker_text").asText(""));
                if (!text.isBlank()) sections.add(text);
            }
        }
        if (sections.isEmpty() && script != null) {
            for (String paragraph : script.split("\\n\\s*\\n")) {
                String text = cleanNarration(paragraph);
                if (text.split("\\s+").length >= 8) sections.add(text);
            }
        }
        if (sections.isEmpty()) {
            sections.add("Let's break down " + topic + " step by step, from the core idea to how you use it in practice.");
        }

        List<Scene> scenes = new ArrayList<>();
        scenes.add(new Scene("Intro", "Welcome! In the next few minutes you'll learn " + topic
                + ", why it matters, and how to use it in practice.",
                Map.of("layout", "title-slide", "theme", THEME, "components", List.of(
                        Map.of("type", "heading", "label", shortTitle(topic)),
                        Map.of("type", "paragraph", "data", Map.of("body", "A practical, no-fluff walkthrough"))))));
        for (int i = 0; i < sections.size() && scenes.size() < MAX_SCENES - 1; i++) {
            String narration = sections.get(i);
            List<String> points = keyPoints(narration, 4);
            String heading = points.isEmpty() ? topic : truncateWords(points.get(0), 7);
            scenes.add(new Scene(i == 0 ? "Core concepts" : null, narration,
                    Map.of("layout", i % 2 == 0 ? "standard-content" : "feature-grid", "theme", THEME,
                            "title", heading,
                            "components", i % 2 == 0
                                    ? List.of(Map.of("type", "bullet-list", "data", Map.of("items", points)))
                                    : points.stream().map(p -> (Object) Map.of("type", "card", "label", truncateWords(p, 6), "sublabel", truncateWords(p, 14))).toList())));
        }
        scenes.add(new Scene("Wrap up", "That's the essentials of " + topic
                + ". If this helped, like the video, subscribe for more, and tell me in the comments what you want next.",
                Map.of("layout", "big-number", "theme", THEME, "components", List.of(
                        Map.of("type", "card", "data", Map.of("value", "Subscribe")),
                        Map.of("type", "paragraph", "data", Map.of("body", "New technical deep dives every week"))))));

        String description = "In this video: " + topic + ".\n\n" + String.join(" ", keyPoints(String.join(" ", sections), 3));
        return new Storyboard(topic, description, List.of(), shortTitle(topic), "explained", List.of(), scenes);
    }

    private JsonNode readJson(String content) {
        if (content == null) return null;
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return objectMapper.readTree(trimmed.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String cleanNarration(String text) {
        return text.replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("[*_#`>]", "")
                .replaceAll("\\[(.*?)]\\(.*?\\)", "$1")
                .replaceAll("https?://\\S+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<String> keyPoints(String text, int max) {
        List<String> points = new ArrayList<>();
        for (String sentence : SENTENCE_END.split(text)) {
            String s = sentence.trim();
            if (s.length() >= 12) points.add(truncateWords(s, 14));
            if (points.size() >= max) break;
        }
        if (points.isEmpty() && !text.isBlank()) points.add(truncateWords(text, 14));
        return points;
    }

    private static String truncateWords(String text, int maxWords) {
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) return text.trim().replaceAll("[.!?]+$", "");
        return String.join(" ", java.util.Arrays.copyOf(words, maxWords)) + "...";
    }

    private static String shortTitle(String topic) {
        return truncateWords(topic, 5).replace("...", "");
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

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Builds YouTube chapter lines from scene start times; YouTube needs >= 3 chapters, the first at 0:00, each >= 10s. */
    public static List<String> chapters(List<Scene> scenes, List<Double> sceneStarts) {
        List<String> lines = new ArrayList<>();
        double lastStart = -100;
        for (int i = 0; i < scenes.size() && i < sceneStarts.size(); i++) {
            String chapter = scenes.get(i).chapter();
            if (chapter == null) continue;
            double start = lines.isEmpty() ? 0 : sceneStarts.get(i);
            if (start - lastStart < 10) continue;
            lines.add(timestamp(start) + " " + chapter);
            lastStart = start;
        }
        return lines.size() >= 3 ? lines : List.of();
    }

    private static String timestamp(double seconds) {
        int total = (int) Math.floor(seconds);
        return String.format("%d:%02d", total / 60, total % 60);
    }

    public record Scene(String chapter, String narration, Map<String, Object> visual) {}

    public record Storyboard(String title, String description, List<String> tags, String thumbnailText,
                             String thumbnailSubtitle, List<String> badges, List<Scene> scenes) {}
}
