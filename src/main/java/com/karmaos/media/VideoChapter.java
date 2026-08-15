package com.karmaos.media;

import lombok.Data;

@Data
public class VideoChapter {
    private String title;
    private double startTime; // seconds from video start
}