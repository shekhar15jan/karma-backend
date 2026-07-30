package com.karmaos.media;

import java.util.List;

public interface VoiceProvider {
    String getName();
    String getDefaultModel();
    List<String> getAvailableModels();
    VoiceResponse synthesize(VoiceRequest request);
    boolean testConnection();
}
