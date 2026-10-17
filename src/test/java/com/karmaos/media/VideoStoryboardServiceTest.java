package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.media.VideoStoryboardService.Scene;
import com.karmaos.media.VideoStoryboardService.Storyboard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VideoStoryboardServiceTest {

    private final VideoStoryboardService service = new VideoStoryboardService(null, new ObjectMapper());

    private static final String LLM_OUTPUT = """
            Here is your storyboard:
            ```json
            {
              "title": "Kafka in 5 Minutes",
              "description": "Learn Kafka fast.",
              "tags": ["kafka", "java"],
              "thumbnail_text": "KAFKA FAST",
              "thumbnail_subtitle": "explained",
              "badges": ["Java", "Kafka"],
              "scenes": [
                {"chapter": "Intro", "narration": "Kafka moves trillions of events a day. Here's how it works.",
                 "visual": {"layout": "big-number", "components": [{"type": "card", "data": {"value": "7T"}}]}},
                {"narration": "Producers write events to topics, and consumers read them at their own pace.",
                 "visual": {"layout": "architecture-diagram", "title": "Data flow", "components": [
                   {"id": "p 1!", "type": "microservice-node", "label": "Producer", "fill": "blue", "data": {"stack": ["java"]}},
                   {"type": "hologram", "label": "dropped"},
                   {"type": "database", "label": "Postgres", "line": "#00E5FF"}]}},
                {"chapter": "Code", "narration": "Here is a minimal producer you can run today on your laptop.",
                 "visual": {"layout": "made-up-layout", "components": []}},
                {"narration": "too short"}
              ]
            }
            ```
            """;

    @Test
    void parsesFencedJsonAndSanitizesVisuals() {
        Storyboard storyboard = service.parse(LLM_OUTPUT, "Kafka");

        assertNotNull(storyboard);
        assertEquals("Kafka in 5 Minutes", storyboard.title());
        assertEquals(List.of("Java", "Kafka"), storyboard.badges());
        assertEquals(3, storyboard.scenes().size(), "scene with < 5 narration words is dropped");

        Map<String, Object> diagram = storyboard.scenes().get(1).visual();
        assertEquals("codeatcloud", diagram.get("theme"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) diagram.get("components");
        assertEquals(2, components.size(), "unknown component types are dropped");
        assertEquals("p1", components.get(0).get("id"));
        assertFalse(components.get(0).containsKey("fill"), "non-hex colours would fail renderer validation");
        assertEquals("#00E5FF", components.get(1).get("line"));
    }

    @Test
    void unknownLayoutAndEmptyComponentsFallBackToBullets() {
        Storyboard storyboard = service.parse(LLM_OUTPUT, "Kafka");
        Map<String, Object> visual = storyboard.scenes().get(2).visual();

        assertEquals("standard-content", visual.get("layout"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) visual.get("components");
        assertEquals("bullet-list", components.get(0).get("type"));
    }

    @Test
    void rejectsOutputWithTooFewScenes() {
        assertNull(service.parse("{\"scenes\": [{\"narration\": \"one two three four five six\"}]}", "x"));
        assertNull(service.parse("not json at all", "x"));
    }

    @Test
    void fallbackUsesScriptBodySections() {
        String script = "{\"script_body\": [{\"speaker_text\": \"Docker packages your app with everything it needs to run anywhere.\"},"
                + "{\"speaker_text\": \"Containers share the host kernel, so they start in milliseconds instead of minutes.\"}]}";
        Storyboard storyboard = service.fallback("Docker Basics", script);

        assertEquals(4, storyboard.scenes().size(), "intro + 2 sections + call to action");
        assertTrue(storyboard.scenes().get(1).narration().startsWith("Docker packages"));
        assertEquals("title-slide", storyboard.scenes().get(0).visual().get("layout"));
    }

    @Test
    void chaptersStartAtZeroAndSkipShortSections() {
        List<Scene> scenes = List.of(
                new Scene("Intro", "n", Map.of()),
                new Scene("Too close", "n", Map.of()),
                new Scene("Concepts", "n", Map.of()),
                new Scene(null, "n", Map.of()),
                new Scene("Recap", "n", Map.of()));
        List<String> chapters = VideoStoryboardService.chapters(scenes, List.of(3.5, 8.0, 21.0, 40.0, 75.2));

        assertEquals(List.of("0:00 Intro", "0:21 Concepts", "1:15 Recap"), chapters);
    }

    @Test
    void fewerThanThreeChaptersYieldsNone() {
        List<Scene> scenes = List.of(new Scene("Intro", "n", Map.of()), new Scene("End", "n", Map.of()));
        assertTrue(VideoStoryboardService.chapters(scenes, List.of(3.0, 60.0)).isEmpty());
    }
}
