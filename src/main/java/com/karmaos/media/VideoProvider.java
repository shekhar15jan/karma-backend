package com.karmaos.media;

import java.util.List;

public interface VideoProvider {
    String getName();
    String getDefaultModel();
    List<String> getAvailableModels();
    VideoResponse generate(VideoRequest request);
    boolean testConnection();
}
