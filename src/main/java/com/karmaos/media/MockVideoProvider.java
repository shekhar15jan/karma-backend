package com.karmaos.media;

import java.util.List;

public class MockVideoProvider implements VideoProvider {

    @Override
    public String getName() { return "mock-video"; }

    @Override
    public String getDefaultModel() { return "mock-video-v1"; }

    @Override
    public List<String> getAvailableModels() { return List.of("mock-video-v1"); }

    @Override
    public VideoResponse generate(VideoRequest request) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return VideoResponse.ok(
            "https://example.com/mock-video-" + System.currentTimeMillis() + ".mp4",
            15
        );
    }

    @Override
    public boolean testConnection() { return true; }
}
