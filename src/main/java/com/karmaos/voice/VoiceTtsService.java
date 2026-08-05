package com.karmaos.voice;

import com.karmaos.media.VoiceRequest;
import com.karmaos.media.VoiceResponse;
import com.karmaos.media.VoiceProvider;
import com.karmaos.media.VoiceProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VoiceTtsService {

    private static final Logger log = LoggerFactory.getLogger(VoiceTtsService.class);

    private static final Map<String, String> LANGUAGE_VOICE_MAP = new LinkedHashMap<>();
    static {
        LANGUAGE_VOICE_MAP.put("en", "en-IN-PrabhatNeural");
        LANGUAGE_VOICE_MAP.put("hi", "hi-IN-SwaraNeural");
        LANGUAGE_VOICE_MAP.put("mr", "mr-IN-AarohiNeural");
        LANGUAGE_VOICE_MAP.put("ta", "ta-IN-PallaviNeural");
        LANGUAGE_VOICE_MAP.put("te", "te-IN-ShrutiNeural");
        LANGUAGE_VOICE_MAP.put("bn", "bn-IN-TanishaaNeural");
        LANGUAGE_VOICE_MAP.put("gu", "gu-IN-DhwaniNeural");
        LANGUAGE_VOICE_MAP.put("kn", "kn-IN-SapnaNeural");
        LANGUAGE_VOICE_MAP.put("ml", "ml-IN-SobhanaNeural");
        LANGUAGE_VOICE_MAP.put("ur", "ur-IN-GulNeural");
        LANGUAGE_VOICE_MAP.put("fr", "fr-FR-DeniseNeural");
        LANGUAGE_VOICE_MAP.put("de", "de-DE-KatjaNeural");
        LANGUAGE_VOICE_MAP.put("ja", "ja-JP-NanamiNeural");
        LANGUAGE_VOICE_MAP.put("zh", "zh-CN-XiaoxiaoNeural");
    }

    private static final Map<String, String> LANGUAGE_NAMES = new LinkedHashMap<>();
    static {
        LANGUAGE_NAMES.put("en", "English");
        LANGUAGE_NAMES.put("hi", "Hindi");
        LANGUAGE_NAMES.put("mr", "Marathi");
        LANGUAGE_NAMES.put("ta", "Tamil");
        LANGUAGE_NAMES.put("te", "Telugu");
        LANGUAGE_NAMES.put("bn", "Bengali");
        LANGUAGE_NAMES.put("gu", "Gujarati");
        LANGUAGE_NAMES.put("kn", "Kannada");
        LANGUAGE_NAMES.put("ml", "Malayalam");
        LANGUAGE_NAMES.put("ur", "Urdu");
        LANGUAGE_NAMES.put("fr", "French");
        LANGUAGE_NAMES.put("de", "German");
        LANGUAGE_NAMES.put("ja", "Japanese");
        LANGUAGE_NAMES.put("zh", "Chinese");
    }

    private static final List<String> AVAILABLE_VOICES = List.of(
            "en-IN-NeerjaNeural", "en-US-AriaNeural", "en-US-JennyNeural", "en-GB-SoniaNeural",
            "hi-IN-SwaraNeural", "hi-IN-MadhurNeural", "mr-IN-AarohiNeural", "mr-IN-ManoharNeural",
            "ta-IN-PallaviNeural", "te-IN-ShrutiNeural", "bn-IN-TanishaaNeural",
            "gu-IN-DhwaniNeural", "kn-IN-SapnaNeural", "ml-IN-SobhanaNeural",
            "ur-IN-GulNeural", "fr-FR-DeniseNeural", "de-DE-KatjaNeural",
            "ja-JP-NanamiNeural", "zh-CN-XiaoxiaoNeural");

    private final VoiceProviderFactory voiceProviderFactory;

    public VoiceTtsService(VoiceProviderFactory voiceProviderFactory) {
        this.voiceProviderFactory = voiceProviderFactory;
    }

    public VoiceTtsResponse synthesize(VoiceTtsRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return VoiceTtsResponse.error("Text is empty");
        }

        String language = request.getLanguage() != null && !request.getLanguage().isBlank()
                ? request.getLanguage().toLowerCase() : "en";
        String voice = request.getVoice();
        if (voice == null || voice.isBlank()) {
            voice = LANGUAGE_VOICE_MAP.getOrDefault(language, "en-IN-PrabhatNeural");
        }

        try {
            VoiceProvider provider = voiceProviderFactory.getProvider("edge-tts", null);
            VoiceRequest vr = new VoiceRequest(request.getText());
            vr.setVoice(voice);
            vr.setSpeed(1.0);

            long start = System.currentTimeMillis();
            VoiceResponse response = provider.synthesize(vr);
            long latency = System.currentTimeMillis() - start;

            if (!response.isSuccess()) {
                log.warn("TTS synthesis failed: {}", response.getErrorMessage());
                return VoiceTtsResponse.error(response.getErrorMessage());
            }
            return VoiceTtsResponse.ok(response.getAudioUrl(), voice, language, latency);
        } catch (Exception e) {
            log.error("TTS synthesis error", e);
            return VoiceTtsResponse.error("TTS failed: " + e.getMessage());
        }
    }

    public Map<String, String> getLanguageVoices() {
        return new LinkedHashMap<>(LANGUAGE_VOICE_MAP);
    }

    public List<String> getAvailableVoices() {
        return AVAILABLE_VOICES;
    }

    public String languageName(String code) {
        return LANGUAGE_NAMES.getOrDefault(code, "English");
    }
}
