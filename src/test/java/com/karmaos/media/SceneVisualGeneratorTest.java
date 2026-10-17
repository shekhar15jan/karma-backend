package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SceneVisualGeneratorTest {

    private final SceneVisualGenerator generator = new SceneVisualGenerator(null, null, null, new ObjectMapper());

    @Test
    void parsesOnlyRequestedScenesAndSanitizes() {
        String llm = """
                ```json
                {"scenes": [
                  {"index": 1, "visual": {"layout": "architecture-diagram", "title": "Flow", "components": [
                    {"id": "a b", "type": "microservice-node", "label": "API", "fill": "red"},
                    {"type": "hologram", "label": "x"},
                    {"type": "database", "label": "Postgres"}]}},
                  {"index": 2, "visual": {"layout": "nope", "components": [{"type": "bullet-list", "data": {"items": ["a", "b"]}}]}},
                  {"index": 5, "visual": {"layout": "quote", "components": [{"type": "paragraph", "data": {"body": "ignored"}}]}}
                ]}
                ```""";
        Map<Integer, Map<String, Object>> parsed = generator.parse(llm, List.of(1, 2));

        assertEquals(List.of(1, 2), List.copyOf(parsed.keySet()));
        Map<String, Object> diagram = parsed.get(1);
        assertEquals("codeatcloud", diagram.get("theme"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) diagram.get("components");
        assertEquals(2, components.size(), "unknown component types are dropped");
        assertEquals("ab", components.get(0).get("id"));
        assertFalse(components.get(0).containsKey("fill"), "non-hex colours would fail renderer validation");
        assertEquals("standard-content", parsed.get(2).get("layout"), "unknown layouts fall back");
    }

    @Test
    void visualWithoutDrawableComponentsIsRejected() {
        assertTrue(generator.parse("{\"scenes\":[{\"index\":0,\"visual\":{\"layout\":\"quote\",\"components\":[]}}]}", List.of(0)).isEmpty());
        assertTrue(generator.parse("not json", List.of(0)).isEmpty());
    }

    @Test
    void fallbackIsADarkBulletSlideNotAPlaceholder() {
        Map<String, Object> spec = generator.fallbackVisual(
                "Kafka stores events in partitions. Each partition is an ordered log. Consumers track offsets to resume.", "Kafka");

        assertEquals("codeatcloud", spec.get("theme"));
        assertEquals("Kafka stores events in partitions", spec.get("title"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) spec.get("components");
        assertEquals("bullet-list", components.get(0).get("type"));
    }

    @Test
    void scenesWithSpecsAreLeftAlone() {
        VideoScene withSpec = new VideoScene();
        withSpec.setSpec("{\"layout\":\"quote\"}");
        assertEquals(0, generator.fillMissingVisuals(List.of(withSpec), null, null, "t"));
        assertEquals("{\"layout\":\"quote\"}", withSpec.getSpec());
    }

    @Test
    void titleSlideHookWithContentCardsBecomesAComparison() {
        VideoScene hook = new VideoScene();
        hook.setSpec("{\"layout\":\"title-slide\",\"title\":\"It works on my machine\",\"components\":["
                + "{\"type\":\"card\",\"label\":\"Local\"},{\"type\":\"warning-card\",\"label\":\"Staging\"}]}");
        VideoScene later = new VideoScene();
        later.setSpec("{\"layout\":\"title-slide\",\"components\":[{\"type\":\"card\"},{\"type\":\"card\"}]}");

        assertTrue(generator.promoteHookLayout(List.of(hook, later)));
        assertTrue(hook.getSpec().contains("\"layout\":\"comparison\""));
        assertTrue(later.getSpec().contains("title-slide"), "only the opening scene is the hook");
    }

    @Test
    void plainTitleSlideHookIsLeftAlone() {
        VideoScene hook = new VideoScene();
        String spec = "{\"layout\":\"title-slide\",\"components\":[{\"type\":\"heading\"},{\"type\":\"badge\"},{\"type\":\"card\"}]}";
        hook.setSpec(spec);

        assertFalse(generator.promoteHookLayout(List.of(hook)));
        assertEquals(spec, hook.getSpec());
    }
}
