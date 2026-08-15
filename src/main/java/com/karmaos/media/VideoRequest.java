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
    /** Optional background music (data URI or URL) for the Remotion provider. */
    private String musicUrl;
    /** Intro card branding (Remotion provider). */
    private String introTitle;
    private String introSubtitle;
    private String brandingLogo;
    private String brandingFooter;

    /** Caption settings for the Remotion provider. */
    private VideoCaptionsConfig captions;
    
    /** Chapter markers for YouTube (Remotion provider). */
    private List<VideoChapter> chapters;
}
