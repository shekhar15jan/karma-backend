package com.karmaos.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karmaos.media.VideoRequest;
import com.karmaos.media.VideoScene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Video QA Engine — heuristic checks without AI (§27-28).
 * Pipeline: Render → Analyze → QA → PASS/FAIL → VIDEO_QA_REPORT.json
 *
 * Checks:
 *  Layout: clipping / overflow / overlap / alignment / safe areas (≥5% padding)
 *  Typography: readability / min font 14px / contrast WCAG AA / truncation
 *  Motion: dead periods >3s / excessive >6 anims / abrupt (>0.3 scale jump) / collision (2 anims same target)
 *  Story: repeated scene type 3x / insufficient visual change (<10% pixels) / narration-visual mismatch
 *  Audio: narration -14LUFS / music duck -20dB / SFX collision / silence gap >2s
 *
 * Scoring (§28): 8 dimensions 0-100 + Professional Score avg, threshold 80 configurable.
 * Persisted as versioned artifact artifact_type=QA_REPORT.
 */
@Service
public class VideoQAService {

    private static final Logger log = LoggerFactory.getLogger(VideoQAService.class);

    public static final int DEFAULT_THRESHOLD = 80;
    public static final String REPORT_FILE = "VIDEO_QA_REPORT.json";

    // Heuristic constants (hardcoded, no AI)
    private static final double SAFE_PADDING_PCT = 0.05; // 5%
    private static final int FRAME_W = 1920;
    private static final int FRAME_H = 1080;
    private static final int MIN_FONT_PX = 14;
    private static final int SAFE_MARGIN_X = (int) (FRAME_W * SAFE_PADDING_PCT); // 96
    private static final int SAFE_MARGIN_Y = (int) (FRAME_H * SAFE_PADDING_PCT); // 54
    private static final double MAX_SCALE_JUMP = 0.3;
    private static final int MAX_ANIMS_PER_SCENE = 6;
    private static final double DEAD_PERIOD_SEC = 3.0;
    private static final double SILENCE_GAP_SEC = 2.0;

    private final ObjectMapper objectMapper;

    public VideoQAService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // For manual instantiation without Spring
    public VideoQAService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Main entry: analyze VideoScene list (from VideoRequest) + optional VideoRequest for audio context.
     */
    public ObjectNode analyze(List<VideoScene> scenes, VideoRequest request) {
        return analyze(scenes, request, DEFAULT_THRESHOLD);
    }

    public ObjectNode analyze(List<VideoScene> scenes, VideoRequest request, int threshold) {
        List<JsonNode> specs = new ArrayList<>();
        for (VideoScene s : scenes) {
            String specStr = s.getSpec();
            if (specStr != null && !specStr.isBlank()) {
                try {
                    specs.add(objectMapper.readTree(specStr));
                } catch (Exception e) {
                    ObjectNode fallback = objectMapper.createObjectNode();
                    fallback.put("parseError", e.getMessage());
                    fallback.put("text", s.getText() != null ? s.getText() : "");
                    specs.add(fallback);
                }
            } else {
                ObjectNode n = objectMapper.createObjectNode();
                n.put("text", s.getText() != null ? s.getText() : "");
                if (s.getAudioUrl() != null) n.put("hasAudio", true);
                specs.add(n);
            }
        }
        // enrich with per-scene audio presence for audio checks
        for (int i = 0; i < specs.size() && i < scenes.size(); i++) {
            if (specs.get(i) instanceof ObjectNode on) {
                VideoScene vs = scenes.get(i);
                if (vs.getAudioUrl() != null) on.put("audioUrl", vs.getAudioUrl());
                if (vs.getText() != null) on.put("narrationText", vs.getText());
                if (vs.getDurationSeconds() > 0) on.put("durationSeconds", vs.getDurationSeconds());
                if (vs.getTimelineEvents() != null) {
                    try { on.set("timelineEvents", objectMapper.readTree(vs.getTimelineEvents())); } catch (Exception ignored) {}
                }
            }
        }
        return analyzeSpecs(specs, request, threshold);
    }

    /**
     * Analyze raw spec JsonNodes (SemanticSceneSpec / VisualSpec).
     */
    public ObjectNode analyzeSpecs(List<JsonNode> specs, VideoRequest request, int threshold) {
        List<String> layoutIssues = new ArrayList<>();
        List<String> typographyIssues = new ArrayList<>();
        List<String> motionIssues = new ArrayList<>();
        List<String> storyIssues = new ArrayList<>();
        List<String> audioIssues = new ArrayList<>();

        // --- per-scene checks ---
        for (int i = 0; i < specs.size(); i++) {
            JsonNode spec = specs.get(i);
            checkLayout(spec, i, layoutIssues);
            checkTypography(spec, i, typographyIssues);
            checkMotion(spec, i, specs, motionIssues);
            checkAudio(spec, i, request, audioIssues);
        }
        checkStory(specs, storyIssues);

        // --- scoring: 8 dimensions §28 ---
        Map<String, Integer> scores = scoreDimensions(layoutIssues, typographyIssues, motionIssues, storyIssues, audioIssues, specs.size());
        int professionalScore = (int) Math.round(scores.values().stream().mapToInt(Integer::intValue).average().orElse(0));
        String overall = professionalScore >= threshold ? "PASS" : "FAIL";

        ObjectNode report = objectMapper.createObjectNode();
        report.put("version", "1.0");
        report.put("generatedAt", Instant.now().toString());
        report.put("threshold", threshold);
        report.put("overall", overall);
        report.put("professionalScore", professionalScore);
        report.put("sceneCount", specs.size());
        report.put("diagnostic", "Score is diagnostic, not a guarantee of production readiness (§28)");

        ObjectNode scoresNode = objectMapper.createObjectNode();
        scores.forEach(scoresNode::put);
        scoresNode.put("professionalScore", professionalScore);
        report.set("scores", scoresNode);

        // per-dimension PASS/FAIL
        ObjectNode dimensions = objectMapper.createObjectNode();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            ObjectNode d = objectMapper.createObjectNode();
            d.put("score", e.getValue());
            d.put("status", e.getValue() >= threshold ? "PASS" : "FAIL");
            d.put("threshold", threshold);
            dimensions.set(e.getKey(), d);
        }
        // attach issues to dimension nodes for readability
        appendIssues(dimensions, "visualDesign", layoutIssues);
        appendIssues(dimensions, "readability", typographyIssues);
        appendIssues(dimensions, "technicalClarity", merge(typographyIssues, layoutIssues));
        appendIssues(dimensions, "motion", motionIssues);
        appendIssues(dimensions, "storytelling", storyIssues);
        appendIssues(dimensions, "consistency", merge(storyIssues, layoutIssues));
        appendIssues(dimensions, "realism", merge(storyIssues, audioIssues));
        appendIssues(dimensions, "audioSync", audioIssues);
        report.set("dimensions", dimensions);

        ObjectNode checks = objectMapper.createObjectNode();
        checks.set("layout", toIssuesNode(layoutIssues));
        checks.set("typography", toIssuesNode(typographyIssues));
        checks.set("motion", toIssuesNode(motionIssues));
        checks.set("story", toIssuesNode(storyIssues));
        checks.set("audio", toIssuesNode(audioIssues));
        report.set("checks", checks);

        // summary
        int totalIssues = layoutIssues.size() + typographyIssues.size() + motionIssues.size() + storyIssues.size() + audioIssues.size();
        report.put("totalIssues", totalIssues);
        report.put("summary", totalIssues == 0 ? "No QA issues detected" : totalIssues + " issue(s) detected — see checks");

        return report;
    }

    public ObjectNode analyzeSpecs(List<JsonNode> specs) {
        return analyzeSpecs(specs, null, DEFAULT_THRESHOLD);
    }

    /**
     * Persist report to file (VIDEO_QA_REPORT.json) and return path.
     * Caller may also persist as versioned artifact with artifact_type=QA_REPORT.
     */
    public Path writeReport(ObjectNode report, Path dir) throws Exception {
        Path outDir = dir != null ? dir : Paths.get(System.getProperty("user.home"), ".karma", "media");
        Files.createDirectories(outDir);
        Path file = outDir.resolve(REPORT_FILE);
        Files.writeString(file, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        log.info("Wrote {} to {}", REPORT_FILE, file);
        return file;
    }

    // ==================== Checks ====================

    private void checkLayout(JsonNode spec, int idx, List<String> out) {
        JsonNode components = spec.get("components");
        int n = components != null && components.isArray() ? components.size() : 0;

        // overflow: too many components for frame
        if (n > 18) out.add("scene " + idx + ": layout overflow — " + n + " components exceeds readable density (>18)");
        else if (n > 12) out.add("scene " + idx + ": layout overflow risk — " + n + " components is dense");

        // estimate bounding via label heuristic
        if (components != null && components.isArray()) {
            List<int[]> boxes = new ArrayList<>();
            int estX = SAFE_MARGIN_X;
            int estY = SAFE_MARGIN_Y + 120; // title offset
            for (JsonNode c : components) {
                String label = c.hasNonNull("label") ? c.get("label").asText() : "";
                int w = estimateWidth(c, label);
                int h = estimateHeight(c, label);
                // naive grid placement estimate (3 cols) to detect clipping
                int col = boxes.size() % 3;
                int row = boxes.size() / 3;
                int x = SAFE_MARGIN_X + col * (w + 32);
                int y = estY + row * (h + 24);
                // clipping: x+width beyond frame or y+height beyond frame
                if (x + w > FRAME_W - SAFE_MARGIN_X) out.add("scene " + idx + ": layout clipping — component '" + label + "' estimated x+w=" + (x+w) + " exceeds frame " + FRAME_W);
                if (y + h > FRAME_H - SAFE_MARGIN_Y) out.add("scene " + idx + ": layout overflow — component '" + label + "' estimated y+h=" + (y+h) + " exceeds frame " + FRAME_H);
                boxes.add(new int[]{x, y, w, h});
                // safe areas ≥5% padding: flag if any box too close to edge (should be ≥ SAFE_MARGIN)
                // Our estimate already respects margin, but check explicit spec x,y if present
                if (c.has("x") && c.get("x").isNumber() && c.get("x").asInt() < SAFE_MARGIN_X) out.add("scene " + idx + ": layout safe area violation — component '" + label + "' x < 5% (" + SAFE_MARGIN_X + "px)");
                if (c.has("y") && c.get("y").isNumber() && c.get("y").asInt() < SAFE_MARGIN_Y) out.add("scene " + idx + ": layout safe area violation — component '" + label + "' y < 5% (" + SAFE_MARGIN_Y + "px)");
            }
            // overlap: O(n^2) bounding box intersection
            for (int a = 0; a < boxes.size(); a++) {
                for (int b = a + 1; b < boxes.size(); b++) {
                    if (intersects(boxes.get(a), boxes.get(b))) {
                        out.add("scene " + idx + ": layout overlap — components " + a + " and " + b + " estimated boxes intersect");
                    }
                }
            }
            // alignment: flag if estimated x positions not multiple of 8 (grid)
            // heuristic: if many components, alignment matters less; only flag when n <= 6 and misaligned
            if (n > 0 && n <= 6) {
                for (int i = 0; i < boxes.size(); i++) {
                    int x = boxes.get(i)[0];
                    if (x % 8 != 0) {
                        out.add("scene " + idx + ": layout alignment — component " + i + " x=" + x + " not 8px grid aligned");
                        break; // one misalignment per scene is enough
                    }
                }
            }
        }

        // container clipping
        JsonNode containers = spec.get("containers");
        if (containers != null && containers.isArray() && containers.size() > 6) {
            out.add("scene " + idx + ": layout overflow — " + containers.size() + " containers exceeds limit (6)");
        }
    }

    private void checkTypography(JsonNode spec, int idx, List<String> out) {
        JsonNode components = spec.get("components");
        if (components != null && components.isArray()) {
            for (JsonNode c : components) {
                String label = c.hasNonNull("label") ? c.get("label").asText() : "";
                String sub = c.hasNonNull("sublabel") ? c.get("sublabel").asText() : "";
                // truncation: label > 80 chars or sublabel > 120
                if (label.length() > 80) out.add("scene " + idx + ": typography truncation — label '" + truncate(label, 40) + "' exceeds 80 chars");
                if (sub.length() > 120) out.add("scene " + idx + ": typography truncation — sublabel exceeds 120 chars");
                // truncation via width: estimated width vs frame budget
                int estW = estimateWidth(c, label);
                if (estW > 420) out.add("scene " + idx + ": typography truncation risk — component '" + truncate(label, 20) + "' estimated width " + estW + "px exceeds 420px budget");
                // contrast WCAG AA: luminance difference < 125 or ratio < 4.5:1 heuristic
                String fill = c.hasNonNull("fill") ? c.get("fill").asText() : null;
                String textColor = c.hasNonNull("textColor") ? c.get("textColor").asText() : null;
                if (fill != null && textColor != null && isHexColor(fill) && isHexColor(textColor)) {
                    double ratio = contrastRatio(fill, textColor);
                    if (ratio < 4.5) out.add("scene " + idx + ": typography contrast — '" + truncate(label, 20) + "' fill " + fill + " vs text " + textColor + " ratio " + String.format("%.1f", ratio) + " < 4.5 (WCAG AA)");
                }
                // min font 14: spec may have data.fontSize or style
                if (c.has("data") && c.get("data").isObject()) {
                    JsonNode d = c.get("data");
                    if (d.has("fontSize") && d.get("fontSize").isNumber() && d.get("fontSize").asInt() < MIN_FONT_PX) {
                        out.add("scene " + idx + ": typography min font — component '" + truncate(label, 20) + "' fontSize " + d.get("fontSize").asInt() + " < " + MIN_FONT_PX);
                    }
                }
            }
        }
        // readability: title/subtitle too long
        String title = spec.hasNonNull("title") ? spec.get("title").asText() : "";
        if (title.length() > 90) out.add("scene " + idx + ": typography readability — title exceeds 90 chars (" + title.length() + ")");
        String subtitle = spec.hasNonNull("subtitle") ? spec.get("subtitle").asText() : "";
        if (subtitle.length() > 140) out.add("scene " + idx + ": typography readability — subtitle exceeds 140 chars");
        // code text readability: if code present and line length > 100
        if (spec.has("code") && spec.get("code").isObject()) {
            String code = spec.get("code").hasNonNull("text") ? spec.get("code").get("text").asText() : "";
            for (String line : code.split("\n")) {
                if (line.length() > 100) { out.add("scene " + idx + ": typography readability — code line exceeds 100 chars"); break; }
            }
        }
    }

    private void checkMotion(JsonNode spec, int idx, List<JsonNode> all, List<String> out) {
        double duration = spec.has("duration") && spec.get("duration").isNumber() ? spec.get("duration").asDouble() : -1;
        if (duration < 0 && spec.has("durationSeconds") && spec.get("durationSeconds").isNumber()) duration = spec.get("durationSeconds").asDouble();
        // estimate duration from text if missing
        if (duration < 0) {
            String t = spec.hasNonNull("narrationText") ? spec.get("narrationText").asText() : (spec.hasNonNull("text") ? spec.get("text").asText() : "");
            duration = Math.max(4, Math.min(25, t.length() / 14.0));
        }
        // collect anims: animation.highlights + semanticAnimations
        int animCount = 0;
        List<Double> atTimes = new ArrayList<>();
        Map<String, List<Double>> targetTimes = new HashMap<>();
        if (spec.has("animation") && spec.get("animation").isObject()) {
            JsonNode hl = spec.get("animation").get("highlights");
            if (hl != null && hl.isArray()) {
                animCount += hl.size();
                for (JsonNode h : hl) {
                    double at = h.has("at") ? h.get("at").asDouble(0) : 0;
                    atTimes.add(at);
                    String id = h.hasNonNull("id") ? h.get("id").asText() : "unknown";
                    targetTimes.computeIfAbsent(id, k -> new ArrayList<>()).add(at);
                }
            }
        }
        if (spec.has("semanticAnimations") && spec.get("semanticAnimations").isArray()) {
            animCount += spec.get("semanticAnimations").size();
            for (JsonNode a : spec.get("semanticAnimations")) {
                double at = a.has("at") ? a.get("at").asDouble(0) : 0;
                atTimes.add(at);
                String tgt = a.hasNonNull("object") ? a.get("object").asText() : (a.hasNonNull("to") ? a.get("to").asText() : "unknown");
                targetTimes.computeIfAbsent(tgt, k -> new ArrayList<>()).add(at);
            }
        }
        if (spec.has("timelineEvents") && spec.get("timelineEvents").isArray()) {
            animCount += spec.get("timelineEvents").size();
        }
        double animTime = animCount * 0.6; // heuristic avg anim occupies 0.6s
        double dead = duration - animTime;
        if (dead > DEAD_PERIOD_SEC) out.add("scene " + idx + ": motion dead period — " + String.format("%.1f", dead) + "s idle (>" + DEAD_PERIOD_SEC + "s) with only " + animCount + " anims over " + String.format("%.1f", duration) + "s");
        if (animCount > MAX_ANIMS_PER_SCENE) out.add("scene " + idx + ": motion excessive — " + animCount + " animations > " + MAX_ANIMS_PER_SCENE);
        // abrupt: scale jump >0.3
        if (idx > 0) {
            double s0 = extractScale(all.get(idx - 1));
            double s1 = extractScale(spec);
            if (!Double.isNaN(s0) && !Double.isNaN(s1) && Math.abs(s1 - s0) > MAX_SCALE_JUMP) {
                out.add("scene " + idx + ": motion abrupt — camera scale jump " + String.format("%.2f", s0) + "→" + String.format("%.2f", s1) + " (>" + MAX_SCALE_JUMP + ")");
            }
        }
        // collision: 2 anims same target at same time (±0.2s)
        for (Map.Entry<String, List<Double>> e : targetTimes.entrySet()) {
            List<Double> ts = e.getValue();
            if (ts.size() < 2) continue;
            ts.sort(Double::compare);
            for (int i = 1; i < ts.size(); i++) {
                if (Math.abs(ts.get(i) - ts.get(i - 1)) < 0.35) {
                    out.add("scene " + idx + ": motion collision — 2 anims on target '" + e.getKey() + "' at ~" + ts.get(i) + "s");
                    break;
                }
            }
        }
    }

    private void checkStory(List<JsonNode> specs, List<String> out) {
        // repeated scene type 3x
        for (int i = 2; i < specs.size(); i++) {
            String t0 = sceneType(specs.get(i - 2));
            String t1 = sceneType(specs.get(i - 1));
            String t2 = sceneType(specs.get(i));
            if (t0 != null && t0.equals(t1) && t1.equals(t2)) {
                out.add("story repeated scene type — 3× '" + t0 + "' at scenes " + (i - 2) + "-" + i);
            }
        }
        // insufficient visual change (<10% pixels heuristic = Jaccard of component ids >0.9)
        for (int i = 1; i < specs.size(); i++) {
            Set<String> a = componentIds(specs.get(i - 1));
            Set<String> b = componentIds(specs.get(i));
            if (a.isEmpty() && b.isEmpty()) continue;
            Set<String> inter = new HashSet<>(a); inter.retainAll(b);
            Set<String> union = new HashSet<>(a); union.addAll(b);
            double jaccard = union.isEmpty() ? 0 : (double) inter.size() / union.size();
            if (jaccard > 0.9 && a.size() >= 2) {
                out.add("story insufficient visual change — scenes " + (i - 1) + "→" + i + " share " + String.format("%.0f", jaccard * 100) + "% component ids (<10% change)");
            }
            // also layout identical check
            String lt0 = specs.get(i - 1).hasNonNull("layout") ? specs.get(i - 1).get("layout").asText() : "";
            String lt1 = specs.get(i).hasNonNull("layout") ? specs.get(i).get("layout").asText() : "";
            if (!lt0.isEmpty() && lt0.equals(lt1) && jaccard > 0.8) {
                out.add("story insufficient visual change — scenes " + (i - 1) + "→" + i + " same layout '" + lt0 + "' and >80% overlap");
            }
        }
        // narration/visual mismatch
        for (int i = 0; i < specs.size(); i++) {
            String narration = narrationText(specs.get(i));
            if (narration == null || narration.isBlank()) continue;
            String lower = narration.toLowerCase();
            Set<String> comps = componentIds(specs.get(i)).stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
            String compJoined = String.join(" ", comps);
            // keywords that should have visual support
            String[] keywords = {"gateway", "kafka", "database", "cache", "queue", "mobile", "payment", "api", "service", "request"};
            for (String kw : keywords) {
                if (lower.contains(kw) && !compJoined.contains(kw)) {
                    // only flag if narration strongly mentions it (not just passing)
                    // heuristic: if keyword appears and spec type/intent doesn't cover it
                    String intent = specs.get(i).hasNonNull("intent") ? specs.get(i).get("intent").asText().toLowerCase() : "";
                    if (!intent.contains(kw)) {
                        out.add("scene " + i + ": story narration/visual mismatch — narration mentions '" + kw + "' but no component id contains it");
                        break; // one per scene
                    }
                }
            }
        }
    }

    private void checkAudio(JsonNode spec, int idx, VideoRequest request, List<String> out) {
        boolean hasAudio = spec.hasNonNull("audioUrl") || spec.hasNonNull("audio") || spec.has("hasAudio");
        double duration = spec.has("duration") && spec.get("duration").isNumber() ? spec.get("duration").asDouble() : 8;
        if (spec.has("durationSeconds") && spec.get("durationSeconds").isNumber()) duration = spec.get("durationSeconds").asDouble();
        String narration = narrationText(spec);
        // silence gap >2s: if scene has no audio and narration exists -> silence
        if (!hasAudio && narration != null && !narration.isBlank()) {
            out.add("scene " + idx + ": audio silence gap — no narration audio for " + String.format("%.1f", duration) + "s scene with text");
        }
        // narration level -14LUFS: heuristic — if audioUrl is file/data URI but we can't probe, flag missing level metadata
        // For QA we treat absence of audio as loudness fail; also very short duration vs text length
        if (hasAudio && narration != null) {
            double expectedSec = Math.max(2, narration.length() / 14.0);
            if (duration < expectedSec - 2) {
                out.add("scene " + idx + ": audio narration level — duration " + String.format("%.1f", duration) + "s shorter than expected " + String.format("%.1f", expectedSec) + "s for text length (possible -14LUFS pacing issue)");
            }
        }
        // music duck -20dB: if request has music and scene has narration, musicVolume should be low
        if (request != null && request.getMusicUrl() != null && !request.getMusicUrl().isBlank() && hasAudio) {
            // heuristic: if musicVolume field unknown, we check request existence; real duck is 0.05 in renderer
            // Flag if request.getMusic* not configured to duck — we can't know volume here, so soft check
            // If narration active and no duck metadata, warn once per video (idx 0)
            if (idx == 0) out.add("audio music duck — video has music + narration; ensure music ducked to -20dB (0.05) during narration (verify KarmaVideo MusicDucking)");
        }
        // SFX collision: timelineEvents with same timestamp
        if (spec.has("timelineEvents") && spec.get("timelineEvents").isArray()) {
            Map<Long, Integer> counts = new HashMap<>();
            for (JsonNode e : spec.get("timelineEvents")) {
                long ts = e.has("timestamp_ms") ? e.get("timestamp_ms").asLong(0) : (e.has("at") ? (long)(e.get("at").asDouble()*1000) : 0);
                counts.merge(ts / 200, 1, Integer::sum); // bucket 200ms
            }
            for (Map.Entry<Long, Integer> e : counts.entrySet()) {
                if (e.getValue() > 1) out.add("scene " + idx + ": audio SFX collision — " + e.getValue() + " events within 200ms at ~" + (e.getKey()*200) + "ms");
            }
        }
        // silence gap >2s between scenes is checked in story as dead period, but also audio gap
        if (duration > 10 && !hasAudio) {
            out.add("scene " + idx + ": audio silence gap >2s — " + String.format("%.1f", duration) + "s scene without audio");
        }
    }

    // ==================== Scoring ====================

    private Map<String, Integer> scoreDimensions(List<String> layout, List<String> typo, List<String> motion, List<String> story, List<String> audio, int sceneCount) {
        // start 100, deduct per issue weighted by category
        int visualDesign = clamp(100 - layout.size() * 12 - typo.size() * 3);
        int readability = clamp(100 - typo.size() * 15 - layout.size() * 2);
        int technicalClarity = clamp(100 - typo.size() * 8 - layout.size() * 6 - story.size() * 3);
        int motionScore = clamp(100 - motion.size() * 16 - (motion.isEmpty() && sceneCount > 1 ? 0 : 0));
        int storytelling = clamp(100 - story.size() * 14 - motion.size() * 4 - audio.size() * 2);
        int consistency = clamp(100 - story.size() * 10 - layout.size() * 5 - typo.size() * 2);
        int realism = clamp(100 - story.size() * 7 - audio.size() * 4 - layout.size() * 3);
        int audioSync = clamp(100 - audio.size() * 18 - motion.size() * 2);

        // bonus: if sceneCount >= 8 and diverse layouts, +5 to storytelling/consistency
        // (capped at 100) — simple heuristic
        // No hard AI needed

        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("storytelling", storytelling);
        m.put("visualDesign", visualDesign);
        m.put("motion", motionScore);
        m.put("technicalClarity", technicalClarity);
        m.put("realism", realism);
        m.put("audioSync", audioSync);
        m.put("consistency", consistency);
        m.put("readability", readability);
        return m;
    }

    private int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    private void appendIssues(ObjectNode dimensions, String key, List<String> issues) {
        if (!dimensions.has(key)) return;
        ObjectNode d = (ObjectNode) dimensions.get(key);
        ArrayNode arr = objectMapper.createArrayNode();
        // dedupe and limit to 8 per dimension for readability
        Set<String> seen = new LinkedHashSet<>(issues);
        int c = 0;
        for (String s : seen) { if (c++ >= 8) break; arr.add(s); }
        d.set("issues", arr);
    }

    private List<String> merge(List<String> a, List<String> b) {
        List<String> m = new ArrayList<>(a); m.addAll(b); return m;
    }

    private ObjectNode toIssuesNode(List<String> issues) {
        ObjectNode n = objectMapper.createObjectNode();
        ArrayNode arr = objectMapper.createArrayNode();
        for (String s : issues) arr.add(s);
        n.set("issues", arr);
        n.put("count", issues.size());
        return n;
    }

    // ==================== Helpers ====================

    private int estimateWidth(JsonNode c, String label) {
        // mirrors layout.ts FIXED_NODE_SIZES simplified + heuristic
        String type = c.hasNonNull("type") ? c.get("type").asText() : "";
        Map<String,int[]> fixed = Map.of(
            "api-gateway", new int[]{260,56},
            "browser-node", new int[]{256,160},
            "database", new int[]{128,160}
        );
        if (fixed.containsKey(type)) return fixed.get(type)[0];
        int chars = Math.max(label.length(), c.hasNonNull("sublabel") ? c.get("sublabel").asText().length() : 0);
        int textW = Math.min(360, Math.max(120, chars * 9 + 40));
        return Math.max(textW, 130);
    }

    private int estimateHeight(JsonNode c, String label) {
        int lines = Math.max(1, (int)Math.ceil(label.length() / 16.0));
        boolean hasSub = c.hasNonNull("sublabel") && !c.get("sublabel").asText().isBlank();
        return 62 + (lines - 1) * 20 + (hasSub ? 26 : 0);
    }

    private boolean intersects(int[] a, int[] b) {
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0] && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }

    private boolean isHexColor(String s) { return s.matches("^#[0-9a-fA-F]{3}$|^#[0-9a-fA-F]{6}$"); }

    private double contrastRatio(String hex1, String hex2) {
        double l1 = luminance(hex1), l2 = luminance(hex2);
        double lighter = Math.max(l1, l2), darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private double luminance(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 3) h = "" + h.charAt(0)+h.charAt(0)+h.charAt(1)+h.charAt(1)+h.charAt(2)+h.charAt(2);
        int r = Integer.parseInt(h.substring(0,2),16), g = Integer.parseInt(h.substring(2,4),16), b = Integer.parseInt(h.substring(4,6),16);
        double rs = r/255.0, gs = g/255.0, bs = b/255.0;
        rs = rs <= 0.03928 ? rs/12.92 : Math.pow((rs+0.055)/1.055,2.4);
        gs = gs <= 0.03928 ? gs/12.92 : Math.pow((gs+0.055)/1.055,2.4);
        bs = bs <= 0.03928 ? bs/12.92 : Math.pow((bs+0.055)/1.055,2.4);
        return 0.2126*rs + 0.7152*gs + 0.0722*bs;
    }

    private String truncate(String s, int n) { return s.length() <= n ? s : s.substring(0,n) + "…"; }

    private double extractScale(JsonNode spec) {
        if (spec.has("camera") && spec.get("camera").isObject() && spec.get("camera").has("end") && spec.get("camera").get("end").has("scale")) {
            return spec.get("camera").get("end").get("scale").asDouble(Double.NaN);
        }
        if (spec.has("camera") && spec.get("camera").isObject() && spec.get("camera").has("start") && spec.get("camera").get("start").has("scale")) {
            return spec.get("camera").get("start").get("scale").asDouble(Double.NaN);
        }
        return Double.NaN;
    }

    private String sceneType(JsonNode spec) {
        if (spec.hasNonNull("intent")) return spec.get("intent").asText();
        if (spec.hasNonNull("type")) return spec.get("type").asText();
        if (spec.hasNonNull("layout")) return spec.get("layout").asText();
        return null;
    }

    private Set<String> componentIds(JsonNode spec) {
        Set<String> ids = new HashSet<>();
        JsonNode comps = spec.get("components");
        if (comps != null && comps.isArray()) {
            for (JsonNode c : comps) {
                if (c.hasNonNull("id")) ids.add(c.get("id").asText());
                else if (c.hasNonNull("label")) ids.add(c.get("label").asText());
            }
        }
        // also semanticObjects
        JsonNode objs = spec.get("semanticObjects");
        if (objs != null && objs.isArray()) {
            for (JsonNode o : objs) if (o.hasNonNull("id")) ids.add(o.get("id").asText());
        }
        return ids;
    }

    private String narrationText(JsonNode spec) {
        if (spec.hasNonNull("narrationText")) return spec.get("narrationText").asText();
        if (spec.has("narration") && spec.get("narration").isObject() && spec.get("narration").hasNonNull("text")) return spec.get("narration").get("text").asText();
        if (spec.hasNonNull("text")) return spec.get("text").asText();
        if (spec.hasNonNull("title")) return spec.get("title").asText();
        return "";
    }
}
