package com.karmaos.voice;

public class VoiceChatRequest {
    private String text;
    private String currentPage;
    private String preferredLanguage;

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
}
