package com.karmaos.media;

import java.util.List;
import java.util.Map;

/**
 * Structured prompt contract for visual asset generation (§18, §21).
 * Never send raw narration to the image generator — first produce this spec,
 * then derive the actual prompt. Asset is background, not full scene.
 *
 * Example JSON (per docs):
 * <pre>
 * {"assetType":"ENVIRONMENT","purpose":"payment infrastructure background",
 *  "style":"premium enterprise","composition":"wide 16:9",
 *  "lighting":"cinematic realistic","negativeRequirements":["no text","no labels"],
 *  "safeArea":{"left":"40%"}}
 * </pre>
 */
public class VisualAssetSpec {

    /** ENVIRONMENT | DIAGRAM | UI | CODE | etc. — high-level intent. */
    private String assetType;
    /** Human purpose: e.g. "payment infrastructure background". Used for classification. */
    private String purpose;
    private String style;
    private String composition;
    private String lighting;
    private List<String> negativeRequirements;
    private Map<String, String> safeArea;

    // OpenAI provider configuration (§20) — configurable, not hardcoded
    private String model;
    private String size; // e.g. 1024x1024, 1792x1024
    private String quality; // standard | hd | auto
    private String aspectRatio; // e.g. 16:9, 1:1
    private Integer numberOfImages; // n
    private List<String> referenceImages;
    private Boolean transparentBackground;

    /** Optional raw prompt override; if null, derived from other fields. */
    private String prompt;

    public VisualAssetSpec() {}

    public VisualAssetSpec(String assetType, String purpose) {
        this.assetType = assetType;
        this.purpose = purpose;
    }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public String getComposition() { return composition; }
    public void setComposition(String composition) { this.composition = composition; }
    public String getLighting() { return lighting; }
    public void setLighting(String lighting) { this.lighting = lighting; }
    public List<String> getNegativeRequirements() { return negativeRequirements; }
    public void setNegativeRequirements(List<String> negativeRequirements) { this.negativeRequirements = negativeRequirements; }
    public Map<String, String> getSafeArea() { return safeArea; }
    public void setSafeArea(Map<String, String> safeArea) { this.safeArea = safeArea; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
    public String getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }
    public Integer getNumberOfImages() { return numberOfImages; }
    public void setNumberOfImages(Integer numberOfImages) { this.numberOfImages = numberOfImages; }
    public List<String> getReferenceImages() { return referenceImages; }
    public void setReferenceImages(List<String> referenceImages) { this.referenceImages = referenceImages; }
    public Boolean getTransparentBackground() { return transparentBackground; }
    public void setTransparentBackground(Boolean transparentBackground) { this.transparentBackground = transparentBackground; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
}
