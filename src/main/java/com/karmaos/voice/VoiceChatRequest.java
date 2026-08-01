package com.karmaos.voice;

import java.util.List;
import java.util.Map;

public class VoiceChatRequest {
    private String text;
    private String currentPage;
    private String preferredLanguage;
    private List<Map<String, String>> history;

    public VoiceChatRequest() {}

    public VoiceChatRequest(String text, String currentPage) {
        this.text = text;
        this.currentPage = currentPage;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getCurrentPage() { return currentPage; }
    public void setCurrentPage(String currentPage) { this.currentPage = currentPage; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public List<Map<String, String>> getHistory() { return history; }
    public void setHistory(List<Map<String, String>> history) { this.history = history; }
}
