package com.karmaos.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karmaos.common.StrictJson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Word-count rules for script narration. Script models ignore the per-scene word counts in the
 * prompt (a measured run asked for 45-61 words and got 27-42, a 4-minute target rendered at
 * 3:14, and a 25-word hook came back at 38 words), so the counts are checked after generation
 * and the scenes that miss are sent back once for a targeted rewrite.
 */
final class NarrationLength {

    /** The opening scene has to land the hook quickly; everything else follows wordsPerScene. */
    static final int HOOK_MAX_WORDS = 25;
    static final int HOOK_MIN_WORDS = 10;
    /** Below this share of the target a scene is short enough to drag the video under its length. */
    static final double MIN_SHARE = 0.8;

    record Range(int min, int max) {
        boolean contains(int words) {
            return words >= min && words <= max;
        }

        int distance(int words) {
            return words < min ? min - words : words > max ? words - max : 0;
        }
    }

    record Miss(int index, int sceneNumber, int words, Range range, String text, String visualTitle) {
    }

    private NarrationLength() {
    }

    static int words(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    static Range rangeFor(int index, int wordsPerScene, int slack) {
        if (index == 0) return new Range(HOOK_MIN_WORDS, HOOK_MAX_WORDS);
        return new Range((int) Math.round(wordsPerScene * MIN_SHARE), wordsPerScene + 2 * slack);
    }

    /** Scenes whose speaker_text falls outside their range. */
    static List<Miss> misses(ArrayNode scenes, int wordsPerScene, int slack) {
        List<Miss> out = new ArrayList<>();
        for (int i = 0; i < scenes.size(); i++) {
            JsonNode scene = scenes.get(i);
            String text = scene.path("speaker_text").asText("");
            if (text.isBlank()) continue;
            Range range = rangeFor(i, wordsPerScene, slack);
            int count = words(text);
            if (!range.contains(count)) {
                out.add(new Miss(i, scene.path("scene_number").asInt(i + 1), count, range, text,
                        scene.path("visual_spec").path("title").asText("")));
            }
        }
        return out;
    }

    static String repairSystemPrompt() {
        return "You edit narration for a technical YouTube explainer. Rewrite each listed scene's narration so its "
                + "word count falls inside the given range. Keep the meaning, facts, terminology and the sentence that "
                + "links it to the next scene. To lengthen, add one concrete detail, command, number or example about the "
                + "same point; never add filler, greetings, or list numbering. To shorten, cut to the single sharpest idea. "
                + "Output ONLY a raw JSON object: {\"scenes\": [{\"scene_number\": <n>, \"speaker_text\": \"...\"}]}";
    }

    static String repairUserMessage(List<Miss> misses) {
        StringBuilder sb = new StringBuilder("Rewrite these scenes:\n\n");
        for (Miss m : misses) {
            sb.append("scene_number ").append(m.sceneNumber())
                    .append(m.index() == 0 ? " (opening hook: state the problem fast)" : "")
                    .append(m.visualTitle().isBlank() ? "" : " - on screen: \"" + m.visualTitle() + "\"")
                    .append("\nrequired: ").append(m.range().min()).append("-").append(m.range().max())
                    .append(" words (currently ").append(m.words()).append(")\nnarration: ")
                    .append(m.text()).append("\n\n");
        }
        return sb.toString();
    }

    /** True when the repair response is a JSON object carrying a scenes array. */
    static boolean isRepairResponse(String content) {
        JsonNode root = StrictJson.extractLastObject(content);
        return root != null && root.path("scenes").isArray() && !root.path("scenes").isEmpty();
    }

    /**
     * Applies rewritten narration for the missed scenes, keeping a rewrite only when it is closer to
     * its range than the original, so a bad rewrite can never make a scene worse.
     *
     * @return the number of scenes updated
     */
    static int apply(ArrayNode scenes, List<Miss> misses, String content) {
        JsonNode root = StrictJson.extractLastObject(content);
        if (root == null || !root.path("scenes").isArray()) return 0;
        Map<Integer, String> rewritten = new HashMap<>();
        for (JsonNode s : root.get("scenes")) {
            String text = s.path("speaker_text").asText("");
            if (s.has("scene_number") && !text.isBlank()) rewritten.put(s.get("scene_number").asInt(), text.trim());
        }
        int updated = 0;
        for (Miss m : misses) {
            String text = rewritten.get(m.sceneNumber());
            if (text == null) continue;
            if (m.range().distance(words(text)) < m.range().distance(m.words())) {
                ((ObjectNode) scenes.get(m.index())).put("speaker_text", text);
                updated++;
            }
        }
        return updated;
    }
}
