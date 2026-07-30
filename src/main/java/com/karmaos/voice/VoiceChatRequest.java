package com.karmaos.voice;

public class VoiceChatRequest {
    private String text;
    private String currentPage;

    public VoiceChatRequest() {}

    public VoiceChatRequest(String text, String currentPage) {
        this.text = text;
        this.currentPage = currentPage;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getCurrentPage() { return currentPage; }
    public void setCurrentPage(String currentPage) { this.currentPage = currentPage; }
}
