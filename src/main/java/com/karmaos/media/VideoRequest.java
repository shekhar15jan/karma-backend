package com.karmaos.media;

import lombok.Data;
import java.util.List;

@Data
public class VideoRequest {
    private String title;
    private String script;
    private String audioUrl;
    private List<String> imageUrls;
    private List<VideoScene> scenes;
    private String model;
    private int fps = 30;
    private String resolution = "1920x1080";
    private double transitionDuration = 1.0;
}
