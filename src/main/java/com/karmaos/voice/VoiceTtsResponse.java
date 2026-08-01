package com.karmaos.voice;

public class VoiceTtsResponse {
    private boolean success;
    private String dataUri;
    private String voice;
    private String language;
    private long latencyMs;
    private String errorMessage;

    public static VoiceTtsResponse ok(String dataUri, String voice, String language, long latencyMs) {
        VoiceTtsResponse r = new VoiceTtsResponse();
        r.success = true;
        r.dataUri = dataUri;
        r.voice = voice;
        r.language = language;
        r.latencyMs = latencyMs;
        return r;
    }

    public static VoiceTtsResponse error(String errorMessage) {
        VoiceTtsResponse r = new VoiceTtsResponse();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getDataUri() { return dataUri; }
    public void setDataUri(String dataUri) { this.dataUri = dataUri; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
