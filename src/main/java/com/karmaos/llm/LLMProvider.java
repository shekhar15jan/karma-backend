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

    default boolean testConnection(String model) {
        LLMRequest request = new LLMRequest("You are a test bot.", "Reply with just: OK");
        request.setModel(model);
        LLMResponse response = chat(request);
        return response != null && response.isSuccess();
    }

    /**
     * Queries the provider API for the live model list for the configured key.
     * Defaults to the statically declared list when the provider has no live
     * catalog endpoint.
     */
    default List<String> fetchAvailableModels() {
        return getAvailableModels();
    }
}
