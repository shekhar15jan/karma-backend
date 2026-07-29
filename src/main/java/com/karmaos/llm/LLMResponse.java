package com.karmaos.llm;

public class LLMResponse {

    private String content;
    private String model;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long latencyMs;
    private boolean success;
    private String errorMessage;

    public LLMResponse() {}

    public static LLMResponse ok(String content, String model, int promptTokens, int completionTokens, long latencyMs) {
        LLMResponse r = new LLMResponse();
        r.content = content;
        r.model = model;
        r.promptTokens = promptTokens;
        r.completionTokens = completionTokens;
        r.totalTokens = promptTokens + completionTokens;
        r.latencyMs = latencyMs;
        r.success = true;
        return r;
    }

    public static LLMResponse error(String errorMessage) {
        LLMResponse r = new LLMResponse();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
