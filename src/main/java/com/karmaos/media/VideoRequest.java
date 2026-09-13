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
    /** "animated" (default) or "static" — static renders each scene as one stable frame. */
    private String sceneMotion = "animated";
    private double transitionDuration = 1.0;
    /** Default scene transition type ("fade", "slide-left", "slide-right", "wipe-left", "wipe-right", "zoom"). */
    private String transition;
    /** Per-scene transition types, index-aligned with scenes (optional). */
    private List<String> sceneTransitions;
    /** Background overlay pattern for scene/visual frames: "grid", "dots" or "plain". */
    private String backgroundPattern;
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

    /** Whether to show intro card — false for immediate hook (Refinement 1). */
    private Boolean enableIntro;
}
