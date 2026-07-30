package com.karmaos.media;

public class VoiceResponse {
    private boolean success;
    private String audioUrl;
    private long latencyMs;
    private String errorMessage;
    private String model;
    private int durationSeconds;

    public static VoiceResponse ok(String audioUrl, String model, long latencyMs, int durationSeconds) {
        VoiceResponse r = new VoiceResponse();
        r.success = true;
        r.audioUrl = audioUrl;
        r.model = model;
        r.latencyMs = latencyMs;
        r.durationSeconds = durationSeconds;
        return r;
    }

    public static VoiceResponse error(String errorMessage) {
        VoiceResponse r = new VoiceResponse();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
}
