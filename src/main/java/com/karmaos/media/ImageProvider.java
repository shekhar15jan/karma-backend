package com.karmaos.media;

import java.util.List;

public interface ImageProvider {
    String getName();
    String getDefaultModel();
    List<String> getAvailableModels();
    ImageResponse generate(ImageRequest request);
    boolean testConnection();

    default List<String> fetchAvailableModels() {
        return getAvailableModels();
    }
}
