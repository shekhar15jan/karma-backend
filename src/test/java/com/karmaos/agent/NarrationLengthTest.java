package com.karmaos.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NarrationLengthTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String words(int n) {
        return String.join(" ", java.util.Collections.nCopies(n, "word"));
    }

    private ArrayNode scenes(int... wordCounts) {
        ArrayNode scenes = mapper.createArrayNode();
        for (int i = 0; i < wordCounts.length; i++) {
            scenes.addObject().put("scene_number", i + 1).put("speaker_text", words(wordCounts[i]))
                    .putObject("visual_spec").put("title", "Scene " + (i + 1));
        }
        return scenes;
    }

    @Test
    void flagsALongHookAndShortBodyScenes() {
        // wordsPerScene 45, slack 8: hook 10-25, body 36-61 (the real run: hook 38, body 27-42)
        List<NarrationLength.Miss> misses = NarrationLength.misses(scenes(38, 42, 27, 50), 45, 8);

        assertEquals(List.of(0, 2), misses.stream().map(NarrationLength.Miss::index).toList());
        assertEquals(new NarrationLength.Range(10, 25), misses.get(0).range());
        assertEquals(new NarrationLength.Range(36, 61), misses.get(1).range());
    }

    @Test
    void appliesOnlyRewritesThatMoveTowardTheRange() {
        ArrayNode scenes = scenes(38, 42, 27);
        List<NarrationLength.Miss> misses = NarrationLength.misses(scenes, 45, 8);
        String response = "{\"scenes\": ["
                + "{\"scene_number\": 1, \"speaker_text\": \"" + words(20) + "\"},"
                + "{\"scene_number\": 3, \"speaker_text\": \"" + words(12) + "\"}]}";

        assertEquals(1, NarrationLength.apply(scenes, misses, response));
        assertEquals(20, NarrationLength.words(scenes.get(0).get("speaker_text").asText()));
        assertEquals(27, NarrationLength.words(scenes.get(2).get("speaker_text").asText()), "a shorter rewrite is rejected");
    }

    @Test
    void scenesInRangeNeedNoRewrite() {
        assertTrue(NarrationLength.misses(scenes(20, 45, 60), 45, 8).isEmpty());
    }

    @Test
    void recognisesOnlyTheRepairShape() {
        assertTrue(NarrationLength.isRepairResponse("```json\n{\"scenes\":[{\"scene_number\":1,\"speaker_text\":\"x\"}]}\n```"));
        assertFalse(NarrationLength.isRepairResponse("{\"title\":\"t\",\"script_body\":[]}"));
        assertFalse(NarrationLength.isRepairResponse("no json"));
    }
}
