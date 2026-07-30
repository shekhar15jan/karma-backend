package com.karmaos.publishing;

public class PublishRequest {
    private String title;
    private String description;
    private String content;
    private String videoUrl;
    private String thumbnailUrl;
    private String privacyStatus = "private";
    private String categoryId = "22";

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getPrivacyStatus() { return privacyStatus; }
    public void setPrivacyStatus(String privacyStatus) { this.privacyStatus = privacyStatus; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
}
