package com.karmaos.media;

import java.util.List;

public class ImageResponse {
    private boolean success;
    private List<String> imageUrls;
    private String revisedPrompt;
    private long latencyMs;
    private String errorMessage;
    private String model;

    public static ImageResponse ok(List<String> imageUrls, String revisedPrompt, String model, long latencyMs) {
        ImageResponse r = new ImageResponse();
        r.success = true;
        r.imageUrls = imageUrls;
        r.revisedPrompt = revisedPrompt;
        r.model = model;
        r.latencyMs = latencyMs;
        return r;
    }

    public static ImageResponse error(String errorMessage) {
        ImageResponse r = new ImageResponse();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public String getRevisedPrompt() { return revisedPrompt; }
    public void setRevisedPrompt(String revisedPrompt) { this.revisedPrompt = revisedPrompt; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
