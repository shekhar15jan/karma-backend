package com.karmaos.media;

import lombok.Data;

/**
 * One scene of a script-driven video: narration text, optional visual spec JSON,
 * optional background image, optional per-scene audio, and target duration in seconds.
 */
@Data
public class VideoScene {
    private String text;
    private String imageUrl;
    private String audioUrl;
    /** Full visual_spec JSON for the scene (Remotion provider). */
    private String spec;
    private int durationSeconds;
    
    /** JSON string for SSML audio markers */
    private String ssmlAudioMarkers;
    
    /** JSON string for visual trigger cues */
    private String visualTriggerCues;
    
    /** JSON string for timeline events */
    private String timelineEvents;
    
    /** JSON string for per-scene captions array */
    private String captions;
}
