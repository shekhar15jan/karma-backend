package com.karmaos.llm;

import java.util.List;
import java.util.Map;

public class LLMRequest {

    private String model;
    private String systemPrompt;
    private String userMessage;
    private List<Map<String, String>> messages;
    private double temperature = 0.7;
    private int maxTokens = 4096;
    private Map<String, Object> additionalParams;

    public LLMRequest() {}

    public LLMRequest(String systemPrompt, String userMessage) {
        this.systemPrompt = systemPrompt;
        this.userMessage = userMessage;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public List<Map<String, String>> getMessages() { return messages; }
    public void setMessages(List<Map<String, String>> messages) { this.messages = messages; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public Map<String, Object> getAdditionalParams() { return additionalParams; }
    public void setAdditionalParams(Map<String, Object> additionalParams) { this.additionalParams = additionalParams; }
}
