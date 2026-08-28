package com.karmaos.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * VisualAssetService — hybrid visual strategy (§16-19, Slice 4).
 * Classifies every visual request as DETERMINISTIC / AI_GENERATED / HYBRID
 * and delegates to {@link ImageProviderFactory}:
 * <ul>
 *   <li>DETERMINISTIC → DiagramImageProvider / LocalImageProvider (text/diagrams/code/labels/architecture symbols)</li>
 *   <li>AI_GENERATED → OpenAIVisualProvider (environments/people/devices/data-center/cinematic)</li>
 *   <li>HYBRID → AI background + deterministic overlay combined (asset as background, not full scene)</li>
 * </ul>
 * Keeps provider abstraction isolated; no business logic inside providers.
 */
@Service
public class VisualAssetService {

    private static final Logger log = LoggerFactory.getLogger(VisualAssetService.class);

    public enum AssetClass {
        DETERMINISTIC,
        AI_GENERATED,
        HYBRID
    }

    // Deterministic signals: text/diagrams/code/labels/architecture symbols (§19)
    private static final Set<String> DETERMINISTIC_KEYWORDS = Set.of(
            "text", "diagram", "code", "label", "architecture", "symbol",
            "uml", "sequence", "flowchart", "chart", "table", "graph",
            "arrow", "node", "component", "terminal", "ide", "browser",
            "dashboard", "container", "database", "service", "api gateway",
            "queue", "kafka", "microservice", "class", "entity", "schema",
            "decision", "process", "rectangle", "cylinder", "sequence diagram"
    );

    // AI-generated signals: environments/people/devices/data-center/cinematic (§19)
    private static final Set<String> AI_KEYWORDS = Set.of(
            "environment", "people", "person", "device", "data center", "datacenter",
            "office", "banking", "cinematic", "realistic", "photo", "landscape",
            "city", "skyline", "conceptual", "establishing", "infrastructure",
            "background", "premium", "enterprise", "server room", "data-center",
            "office interior", "developer workspace", "isometric", "photorealistic"
    );

    private static final Set<String> HYBRID_KEYWORDS = Set.of(
            "hybrid", "overlay", "composite", "background with overlay",
            "deterministic overlay", "ai background with"
    );

    private final ImageProviderFactory imageProviderFactory;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.image.model:}")
    private String openAiDefaultModel;

    public VisualAssetService(ImageProviderFactory imageProviderFactory,
                              ObjectMapper objectMapper,
                              WebClient.Builder webClientBuilder) {
        this.imageProviderFactory = imageProviderFactory;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Classify visual request purpose as DETERMINISTIC / AI_GENERATED / HYBRID per docs/karma-v2/visual-assets.md.
     * HYBRID = deterministic overlay on AI background.
     */
    public AssetClass classify(String purpose) {
        if (purpose == null || purpose.isBlank()) return AssetClass.DETERMINISTIC;
        String lower = purpose.toLowerCase(Locale.ROOT);

        boolean hasHybrid = HYBRID_KEYWORDS.stream().anyMatch(lower::contains);
        if (hasHybrid) return AssetClass.HYBRID;

        boolean hasDeterministic = DETERMINISTIC_KEYWORDS.stream().anyMatch(lower::contains);
        boolean hasAi = AI_KEYWORDS.stream().anyMatch(lower::contains);

        if (hasDeterministic && hasAi) return AssetClass.HYBRID;
        if (hasAi) return AssetClass.AI_GENERATED;
        if (hasDeterministic) return AssetClass.DETERMINISTIC;

        // AssetType hints: environment/cinematic → AI, else deterministic
        if (lower.contains("environment") || lower.contains("cinematic") || lower.contains("establishing")) {
            return AssetClass.AI_GENERATED;
        }
        return AssetClass.DETERMINISTIC;
    }

    /**
     * Generate asset from structured VisualAssetSpec (not raw narration).
     * Delegates via ImageProviderFactory:
     * deterministic → DiagramImageProvider/Local, AI → OpenAIVisualProvider, hybrid → combine.
     */
    public ImageResponse generateAsset(VisualAssetSpec spec) {
        return generateAsset(spec, openAiApiKey, openAiDefaultModel);
    }

    /**
     * Overload allowing caller to supply provider key/model explicitly (e.g. from Provider entity).
     * Model is not hardcoded; read from spec or provider configuration.
     */
    public ImageResponse generateAsset(VisualAssetSpec spec, String apiKey, String configuredModel) {
        if (spec == null) return ImageResponse.error("VisualAssetSpec is required");
        String purpose = spec.getPurpose() != null ? spec.getPurpose()
                : (spec.getAssetType() != null ? spec.getAssetType() : "");
        AssetClass cls = classify(purpose);
        log.info("VisualAsset classify purpose='{}' => {}", purpose, cls);

        String prompt = buildPrompt(spec);
        ImageRequest request = toImageRequest(spec, prompt, configuredModel);

        return switch (cls) {
            case DETERMINISTIC -> generateDeterministic(request);
            case AI_GENERATED -> generateAi(request, apiKey, configuredModel, spec);
            case HYBRID -> generateHybrid(request, apiKey, configuredModel, spec);
        };
    }

    private ImageResponse generateDeterministic(ImageRequest request) {
        // Prefer diagram provider for structured content; Local as fallback
        ImageProvider diagram = imageProviderFactory.getProvider("diagram", null);
        ImageResponse resp = diagram.generate(request);
        if (resp.isSuccess()) return resp;
        log.warn("Diagram provider failed: {}, trying local fallback", resp.getErrorMessage());
        ImageProvider fallback = imageProviderFactory.getFallbackProvider();
        return fallback.generate(request);
    }

    private ImageResponse generateAi(ImageRequest request, String apiKey, String configuredModel, VisualAssetSpec spec) {
        String key = (apiKey != null && !apiKey.isBlank()) ? apiKey : openAiApiKey;
        ImageProvider provider;
        if (key != null && !key.isBlank()) {
            // Use factory if openai registered, else direct instance
            try {
                provider = imageProviderFactory.getProvider("openai", key);
                // If factory returned fallback (local), it means openai not registered — instantiate directly
                if (provider instanceof LocalImageProvider) {
                    provider = createOpenAIProvider(key, spec, configuredModel);
                } else if (provider instanceof OpenAIVisualProvider oai) {
                    applySpecConfig(oai, spec, configuredModel);
                }
            } catch (Exception e) {
                provider = createOpenAIProvider(key, spec, configuredModel);
            }
        } else {
            log.warn("No OpenAI API key configured, falling back to local image provider");
            provider = imageProviderFactory.getFallbackProvider();
        }
        return provider.generate(request);
    }

    private ImageResponse generateHybrid(ImageRequest request, String apiKey, String configuredModel, VisualAssetSpec spec) {
        // Hybrid: AI background + deterministic overlay combined.
        // Generate AI background as primary; deterministic overlay is composited by renderer (Remotion).
        // Keep provider abstraction isolated — return AI asset as background.
        ImageResponse ai = generateAi(request, apiKey, configuredModel, spec);
        if (!ai.isSuccess()) return ai;
        // Optionally generate deterministic overlay placeholder (not composited server-side)
        // The renderer handles compositing; we return the AI background with metadata.
        log.info("Hybrid asset: AI background generated, deterministic overlay to be composited by renderer");
        return ai;
    }

    private OpenAIVisualProvider createOpenAIProvider(String key, VisualAssetSpec spec, String configuredModel) {
        OpenAIVisualProvider p = new OpenAIVisualProvider(key, webClientBuilder, objectMapper);
        applySpecConfig(p, spec, configuredModel);
        return p;
    }

    private void applySpecConfig(OpenAIVisualProvider p, VisualAssetSpec spec, String configuredModel) {
        String model = spec.getModel() != null ? spec.getModel() : configuredModel;
        if (model != null && !model.isBlank()) p.setModel(model);
        else if (configuredModel != null && !configuredModel.isBlank()) p.setModel(configuredModel);
        if (spec.getSize() != null) p.setSize(spec.getSize());
        if (spec.getQuality() != null) p.setQuality(spec.getQuality());
        if (spec.getAspectRatio() != null) p.setAspectRatio(spec.getAspectRatio());
        if (spec.getNumberOfImages() != null) p.setNumberOfImages(spec.getNumberOfImages());
        if (spec.getReferenceImages() != null) p.setReferenceImages(spec.getReferenceImages());
        if (spec.getTransparentBackground() != null) p.setTransparentBackground(spec.getTransparentBackground());
    }

    private ImageRequest toImageRequest(VisualAssetSpec spec, String prompt, String configuredModel) {
        ImageRequest req = new ImageRequest();
        req.setPrompt(prompt);
        String model = spec.getModel() != null ? spec.getModel() : configuredModel;
        if (model != null && !model.isBlank()) req.setModel(model);
        if (spec.getSize() != null) req.setSize(spec.getSize());
        if (spec.getQuality() != null) req.setQuality(spec.getQuality());
        if (spec.getNumberOfImages() != null) req.setN(spec.getNumberOfImages());
        return req;
    }

    /**
     * Build prompt from VisualAssetSpec (not raw narration) per §21 contract.
     * Asset is background, not full scene.
     */
    public String buildPrompt(VisualAssetSpec spec) {
        if (spec.getPrompt() != null && !spec.getPrompt().isBlank()) return spec.getPrompt().trim();
        StringBuilder sb = new StringBuilder();
        if (spec.getAssetType() != null) sb.append(spec.getAssetType()).append(" ");
        if (spec.getPurpose() != null) sb.append(spec.getPurpose());
        if (spec.getStyle() != null) sb.append(", style: ").append(spec.getStyle());
        if (spec.getComposition() != null) sb.append(", composition: ").append(spec.getComposition());
        if (spec.getLighting() != null) sb.append(", lighting: ").append(spec.getLighting());
        if (spec.getNegativeRequirements() != null && !spec.getNegativeRequirements().isEmpty()) {
            sb.append(". Avoid: ").append(String.join(", ", spec.getNegativeRequirements()));
        }
        if (spec.getSafeArea() != null && !spec.getSafeArea().isEmpty()) {
            sb.append(". Safe area: ").append(spec.getSafeArea().toString());
        }
        // Ensure background guidance: assets are backgrounds, not full scenes
        String p = sb.toString().trim();
        if (!p.toLowerCase(Locale.ROOT).contains("no text")) {
            // Add default negative for AI assets to keep text deterministic
            p += ", no text, no labels, no diagrams";
        }
        return p;
    }
}
