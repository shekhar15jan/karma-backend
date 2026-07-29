package com.karmaos.llm;

import java.util.List;
import java.util.Map;

public interface LLMProvider {

    String getName();

    String getDefaultModel();

    List<String> getAvailableModels();

    LLMResponse chat(LLMRequest request);

    LLMResponse chatWithStream(LLMRequest request, java.util.function.Consumer<String> onChunk);

    boolean testConnection();
}
