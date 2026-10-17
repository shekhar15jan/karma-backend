package com.karmaos.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelRankerTest {

    @Test
    void nonChatModelsAreExcluded() {
        assertTrue(ModelRanker.isTextChatModel("gemini-3.5-flash"));
        assertTrue(ModelRanker.isTextChatModel("gemini-3.1-pro-preview"));
        assertFalse(ModelRanker.isTextChatModel("gemini-3.8-flash-tts"));
        assertFalse(ModelRanker.isTextChatModel("gemini-3.1-flash-image"));
        assertFalse(ModelRanker.isTextChatModel("gemini-3.5-transcribe"));
        assertFalse(ModelRanker.isTextChatModel("gemini-robotics-er-2-preview"));
        assertFalse(ModelRanker.isTextChatModel("gemini-nano-banana-2.1"));
    }

    @Test
    void olderGenerationsAreNotPreferredJustForTheirVersionNumber() {
        List<String> ordered = ModelRanker.orderCheapestFirst(List.of("gemini-3.5-flash", "gemini-2.5-flash"));
        assertEquals("gemini-3.5-flash", ordered.get(0));
    }
}
