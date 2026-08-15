package com.karmaos.media;

import lombok.Data;

@Data
public class VideoCaptionsConfig {
    /** Burn captions into video (true) or return SRT/VTT separately (false). */
    private boolean burnIn = true;
    
    /** Caption style preset: youtube, clean, bold, minimal. */
    private String preset = "youtube";
    
    /** Vertical position: 0=top, 1=bottom. */
    private double position = 0.85;
    
    /** Font size in px (at 1080p). */
    private int fontSize = 28;
    
    /** Max chars per line. */
    private int maxCharsPerLine = 42;
}