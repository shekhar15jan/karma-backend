package com.karmaos.voice;

public class VoiceTtsRequest {
    private String text;
    private String language;
    private String voice;

    public VoiceTtsRequest() {}

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
}
