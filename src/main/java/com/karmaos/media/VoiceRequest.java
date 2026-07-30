package com.karmaos.media;

public class VoiceRequest {
    private String text;
    private String model;
    private String voice = "alloy";
    private Double speed = 1.0;

    public VoiceRequest() {}

    public VoiceRequest(String text) {
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }
}
