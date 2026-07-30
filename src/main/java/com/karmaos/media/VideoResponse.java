package com.karmaos.media;

import lombok.Data;

@Data
public class VideoResponse {
    private boolean success;
    private String videoUrl;
    private int durationSeconds;
    private String errorMessage;

    public static VideoResponse ok(String videoUrl, int durationSeconds) {
        VideoResponse r = new VideoResponse();
        r.success = true;
        r.videoUrl = videoUrl;
        r.durationSeconds = durationSeconds;
        return r;
    }

    public static VideoResponse error(String errorMessage) {
        VideoResponse r = new VideoResponse();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }
}
