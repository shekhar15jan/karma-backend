package com.karmaos.media;

import java.util.List;

public interface MusicProvider {

    String getName();

    String getDefaultModel();

    List<String> getAvailableModels();

    List<String> fetchAvailableModels();

    MusicResponse generate(MusicRequest request);

    boolean testConnection();
}
