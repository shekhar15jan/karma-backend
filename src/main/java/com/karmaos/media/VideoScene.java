package com.karmaos.media;

import lombok.Data;

/**
 * One scene of a script-driven video: narration text, optional background image,
 * optional per-scene audio, and target duration in seconds.
 */
@Data
public class VideoScene {
    private String text;
    private String imageUrl;
    private String audioUrl;
    private int durationSeconds;
}
