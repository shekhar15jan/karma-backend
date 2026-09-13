package com.karmaos.media;

public class ImageRequest {
    private String prompt;
    private String model;
    private String size = "1024x1024";
    private String quality = "standard";
    private int n = 1;
    private Long seed;

    public ImageRequest() {}

    public ImageRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
    public int getN() { return n; }
    public void setN(int n) { this.n = n; }
    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }
}
