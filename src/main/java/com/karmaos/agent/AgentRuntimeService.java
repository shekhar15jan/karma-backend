package com.karmaos.agent;

import com.karmaos.common.StrictJson;
import com.karmaos.execution.ExecutionService;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.flow.Flow;
import com.karmaos.flow.FlowRepository;
import com.karmaos.junction.AgentSkill;
import com.karmaos.junction.AgentSkillRepository;
import com.karmaos.llm.LLMProvider;
import com.karmaos.llm.LLMProviderFactory;
import com.karmaos.llm.LLMRequest;
import com.karmaos.llm.LLMResponse;
import com.karmaos.llm.cache.LlmCache;
import com.karmaos.llm.cache.LlmCacheRepository;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.memory.KarmaMemoryService;
import com.karmaos.project.Project;
import com.karmaos.project.ProjectRepository;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderKeyCipher;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.prompt.Prompt;
import com.karmaos.prompt.PromptRepository;
import com.karmaos.settings.RuntimeSettings;
import com.karmaos.skill.Skill;
import com.karmaos.skill.SkillRepository;
import com.karmaos.sourcedocument.SourceDocument;
import com.karmaos.sourcedocument.SourceDocumentRepository;
import com.karmaos.workspace.Workspace;
import com.karmaos.workspace.WorkspaceRepository;
import com.karmaos.media.VisualSpecNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karmaos.execution.Execution;
import com.karmaos.execution.ExecutionRepository;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.execution.ExecutionStepRepository;
import com.karmaos.mcp.McpToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);
    private static final Map<String, Double> COST_PER_TOKEN = Map.of(
        "gemini-1.5-pro", 1.25e-6,
        "gemini-1.5-flash", 0.075e-6
    );

    /** Layouts the karma-renderer video path actually implements (skill contract). */
    /** Single source of truth: mirrors {@link VisualSpecNormalizer#LAYOUTS} so LLM output
     * validation and downstream normalization never disagree on which layouts render. */
    private static final Set<String> ALLOWED_LAYOUTS = VisualSpecNormalizer.LAYOUTS;

    /** Single source of truth: mirrors {@link VisualSpecNormalizer#COMPONENT_TYPES}. Anything
     * outside this set is coerced to card (not rejected) to avoid 103→40 validation failures. */
    private static final Set<String> ALLOWED_COMPONENT_TYPES = VisualSpecNormalizer.COMPONENT_TYPES;

    private final LLMProviderFactory providerFactory;
    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher providerKeyCipher;
    private final PromptRepository promptRepository;
    private final SkillRepository skillRepository;
    private final FlowRepository flowRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final AgentRepository agentRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ExecutionService executionService;
    private final MissionRepository missionRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final KarmaMemoryService karmaMemoryService;
    private final ObjectMapper objectMapper;
    private final RuntimeSettings runtimeSettings;
    private final LlmCacheRepository llmCacheRepository;
    private final McpToolService mcpToolService;
    private final ExecutionRepository executionRepository;
    private final ExecutionStepRepository stepRepository;

    public AgentRuntimeService(LLMProviderFactory providerFactory,
                               ProviderRepository providerRepository,
                               ProviderKeyCipher providerKeyCipher,
                               PromptRepository promptRepository,
                               SkillRepository skillRepository,
                               FlowRepository flowRepository,
                               AgentSkillRepository agentSkillRepository,
                               AgentRepository agentRepository,
                               SourceDocumentRepository sourceDocumentRepository,
                               ExecutionService executionService,
                               MissionRepository missionRepository,
                               ProjectRepository projectRepository,
                               WorkspaceRepository workspaceRepository,
                               KarmaMemoryService karmaMemoryService,
                               ObjectMapper objectMapper,
                                RuntimeSettings runtimeSettings,
                                LlmCacheRepository llmCacheRepository,
                                McpToolService mcpToolService,
                                ExecutionRepository executionRepository,
                                ExecutionStepRepository stepRepository) {
        this.providerFactory = providerFactory;
        this.providerRepository = providerRepository;
        this.providerKeyCipher = providerKeyCipher;
        this.promptRepository = promptRepository;
        this.skillRepository = skillRepository;
        this.flowRepository = flowRepository;
        this.agentSkillRepository = agentSkillRepository;
        this.agentRepository = agentRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.executionService = executionService;
        this.missionRepository = missionRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.karmaMemoryService = karmaMemoryService;
        this.objectMapper = objectMapper;
        this.runtimeSettings = runtimeSettings;
        this.llmCacheRepository = llmCacheRepository;
        this.mcpToolService = mcpToolService;
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
    }

    public ExecutionStep executeAgentStep(UUID executionId, UUID flowId, UUID agentId, int stepOrder,
                                          String contextData, UUID missionId, String reviewerFeedback) {
        ExecutionStep step = new ExecutionStep();
        step.setExecutionId(executionId);
        step.setFlowId(flowId);
        step.setAgentId(agentId);
        step.setStepOrder(stepOrder);
        step.setStepType("LLM_CALL");
        step.setStatus("RUNNING");
        step.setStartedAt(LocalDateTime.now());
        step.setInputData(contextData);
        step = executionService.saveStep(executionId, step);

        try {
            Flow flow = flowRepository.findByIdAndStatus(flowId, "ACTIVE").orElse(null);
            Agent agent = agentRepository.findById(agentId).orElse(null);
            if (agent == null) {
                throw new RuntimeException("Agent not found: " + agentId);
            }

            String distilledContext = StepContextDistiller.distill(contextData);
            String systemPrompt = buildSystemPrompt(agent, flow, missionId);
            String userMessage = buildUserMessage(agent, distilledContext, missionId, reviewerFeedback);

            // Inject MCP tools for this agent (if any) into system prompt
            try {
                var mcpCatalog = mcpToolService.getToolsForAgent(agent.getId());
                String mcpBlock = mcpToolService.buildToolPromptBlock(mcpCatalog);
                if (!mcpBlock.isBlank()) {
                    systemPrompt = systemPrompt + mcpBlock;
                    log.info("MCP: injected {} servers / {} tools into agent {}", mcpCatalog.size(),
                            mcpCatalog.stream().mapToInt(c -> c.tools().size()).sum(), agent.getName());
                }
            } catch (Exception e) {
                log.warn("MCP tool injection failed for agent {}: {}", agent.getName(), e.getMessage());
            }

            Provider primaryProvider = resolveProvider(agent);
            step.setProviderId(primaryProvider != null ? primaryProvider.getId() : null);

            if (isStoryScriptWriter(agent) && isIterativeScriptMode(agent)) {
                String merged = generateScriptIteratively(agent, flow, missionId, contextData, step);
                step.setStatus("COMPLETED");
                step.setOutputData(merged);
                step.setCompletedAt(LocalDateTime.now());
                return executionService.saveStep(executionId, step);
            }

            LLMResponse response = executeWithRetryAndFallback(agent, primaryProvider, step,
                    systemPrompt, userMessage, missionId, resolveMaxTokens(flow, agent, missionId));
            // MCP tool-call loop (up to 5 chained calls) — provider-agnostic
            response = handleMcpToolCalls(agent, primaryProvider, step, systemPrompt, userMessage, missionId, response);
            String modelUsed = step.getModelUsed();

            step.setPromptTokens(response.getPromptTokens());
            step.setCompletionTokens(response.getCompletionTokens());
            step.setTotalTokens(response.getTotalTokens());
            step.setLatencyMs((int) response.getLatencyMs());
            step.setCost(calculateCost(modelUsed, response.getPromptTokens(), response.getCompletionTokens()));

            if (response.isSuccess()) {
                if (isStoryScriptWriter(agent) && !isOutputValidFor(agent, response.getContent())) {
                    log.error("Script Agent exhausted 3 retries without producing a valid script JSON");
                    step.setStatus("FAILED");
                    step.setErrorMessage("Script agent produced no valid script JSON after all retries. "
                            + "Reasoning prose consumed the output budget. Reduce scene count, raise the model "
                            + "output cap, or switch the agent to a non-reasoning model.");
                } else {
                    step.setStatus("COMPLETED");
                    step.setOutputData(response.getContent());
                }
            } else {
                step.setStatus("FAILED");
                step.setErrorMessage(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Agent execution failed: agentId={}, flowId={}", agentId, flowId, e);
            step.setStatus("FAILED");
            step.setErrorMessage(e.getMessage());
        }

        step.setCompletedAt(LocalDateTime.now());
        return executionService.saveStep(executionId, step);
    }

    /**
     * Iteratively generates a full-length script in N ordered chunks. The raw
     * material (research output + source docs) is split into N sections; each
     * chunk call writes a small number of short scenes (5-10s each) with the
     * prior chunks' running summary for narrative coherence. All chunks are
     * merged into one {@code script_body} array, validated once, and returned
     * as the single step output JSON. The output never exceeds the model's
     * per-call token cap because each call writes only a few scenes.
     */
    private String generateScriptIteratively(Agent agent, Flow flow, UUID missionId, String contextData,
                                             ExecutionStep step) throws Exception {
        StoryTarget target = configuredStoryTarget(agent, missionId);
        int scenesPerChunk = configScenesPerChunk(agent);
        // A scene lasts as long as its narration; the old 10s cap tripled the scene count.
        double avgSceneSeconds = Math.max(5.0, target.wordsPerScene() / WORDS_PER_SECOND);
        int totalScenes = Math.max(1, (int) Math.round(target.targetDurationSeconds() / avgSceneSeconds));
        // Cap to configured maxScenes to keep payload/render manageable (was generating 69 for 600s/8.7)
        totalScenes = Math.min(totalScenes, target.maxScenes());
        totalScenes = Math.max(totalScenes, target.minScenes());
        int numChunks = Math.max(1, (int) Math.ceil(totalScenes / (double) scenesPerChunk));

        String rawContent = extractResearchContent(contextData, missionId);
        List<String> chunks = splitContent(rawContent, numChunks);

        Provider primaryProvider = resolveProvider(agent);
        step.setProviderId(primaryProvider != null ? primaryProvider.getId() : null);
        step.setModelUsed(primaryProvider != null ? resolveModel(agent, primaryProvider, resolveLLMProvider(primaryProvider)) : null);

        ArrayNode allScenes = objectMapper.createArrayNode();
        String title = null;
        int sceneCounter = 0;
        StringBuilder runningSummary = new StringBuilder();
        int totalPrompt = 0;
        int totalCompletion = 0;
        long totalLatency = 0L;
        int maxTokens = resolveMaxTokens(flow, agent, missionId);
        int startChunk = 0;
        // Resume: if this step already has partial progress (from per-chunk saves before a failure), load it
        if (step.getOutputData() != null && step.getOutputData().contains("script_body")) {
            try {
                JsonNode existing = StrictJson.extractLastObject(step.getOutputData());
                if (existing != null && existing.has("script_body") && existing.get("script_body").isArray()) {
                    ArrayNode existingBody = (ArrayNode) existing.get("script_body");
                    if (existingBody.size() > 0 && existingBody.size() <= totalScenes) {
                        log.info("Resuming script from partial progress: {} scenes (chunk {}/{})", existingBody.size(), (existingBody.size() / scenesPerChunk) + 1, numChunks);
                        for (JsonNode scene : existingBody) allScenes.add(scene);
                        sceneCounter = existingBody.size();
                        if (existing.hasNonNull("title")) title = existing.get("title").asText();
                        for (JsonNode scene : existingBody) {
                            String label = "";
                            JsonNode spec = scene.hasNonNull("visual_spec") ? scene.get("visual_spec") : null;
                            if (spec != null && spec.hasNonNull("title")) label = spec.get("title").asText();
                            if (label.isBlank() && scene.hasNonNull("speaker_text")) {
                                String st = scene.get("speaker_text").asText();
                                label = st.length() > 90 ? st.substring(0, 90) : st;
                            }
                            if (!label.isBlank()) runningSummary.append(label).append(" | ");
                        }
                        if (runningSummary.length() > 0) runningSummary.append("\n");
                        startChunk = (int) Math.ceil((double) sceneCounter / scenesPerChunk);
                        if (startChunk >= numChunks) {
                            log.info("Resume: already have all {} scenes, will re-validate", sceneCounter);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load resume progress, starting from scratch: {}", e.getMessage());
            }
        }
        // Also check previous FAILED step for same execution/stepOrder (retry after failure creates a new step row)
        if (startChunk == 0 && (step.getOutputData() == null || !step.getOutputData().contains("script_body"))) {
            try {
                List<ExecutionStep> prevSteps = stepRepository.findByExecutionIdOrderByStepOrder(step.getExecutionId());
                for (ExecutionStep ps : prevSteps) {
                    if (ps.getStepOrder() == step.getStepOrder() && "FAILED".equals(ps.getStatus()) && ps.getOutputData() != null && ps.getOutputData().contains("script_body") && !ps.getId().equals(step.getId())) {
                        JsonNode existing = StrictJson.extractLastObject(ps.getOutputData());
                        if (existing != null && existing.has("script_body") && existing.get("script_body").isArray()) {
                            ArrayNode existingBody = (ArrayNode) existing.get("script_body");
                            if (existingBody.size() > 0 && existingBody.size() < totalScenes) {
                                log.info("Resuming script from previous FAILED step {}: {} scenes (chunk {}/{})", ps.getId(), existingBody.size(), (existingBody.size() / scenesPerChunk) + 1, numChunks);
                                for (JsonNode scene : existingBody) allScenes.add(scene);
                                sceneCounter = existingBody.size();
                                if (existing.hasNonNull("title")) title = existing.get("title").asText();
                                for (JsonNode scene : existingBody) {
                                    String label = "";
                                    JsonNode spec = scene.hasNonNull("visual_spec") ? scene.get("visual_spec") : null;
                                    if (spec != null && spec.hasNonNull("title")) label = spec.get("title").asText();
                                    if (label.isBlank() && scene.hasNonNull("speaker_text")) {
                                        String st = scene.get("speaker_text").asText();
                                        label = st.length() > 90 ? st.substring(0, 90) : st;
                                    }
                                    if (!label.isBlank()) runningSummary.append(label).append(" | ");
                                }
                                if (runningSummary.length() > 0) runningSummary.append("\n");
                                startChunk = (int) Math.ceil((double) sceneCounter / scenesPerChunk);
                                break;
                            } else if (existingBody.size() >= totalScenes) {
                                log.info("Resume: previous FAILED step already has all {} scenes, will re-validate", existingBody.size());
                                for (JsonNode scene : existingBody) allScenes.add(scene);
                                sceneCounter = existingBody.size();
                                if (existing.hasNonNull("title")) title = existing.get("title").asText();
                                startChunk = numChunks;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load previous failed step progress: {}", e.getMessage());
            }
        }

        for (int i = startChunk; i < chunks.size(); i++) {
            boolean isFirst = i == 0;
            boolean isLast = i == chunks.size() - 1;
            int scenesThisChunk = isLast ? Math.max(1, totalScenes - sceneCounter) : scenesPerChunk;
            if (scenesThisChunk <= 0) scenesThisChunk = 1;
            final int chunkScenes = scenesThisChunk;

            String chunkSystem = buildChunkSystemPrompt(agent, flow, missionId, target,
                    chunkScenes, isFirst, isLast, sceneCounter + 1);
            String chunkUser = buildChunkUserMessage(agent, missionId, chunks.get(i),
                    runningSummary.toString(), isFirst, isLast, sceneCounter + 1, chunkScenes);

            LLMResponse response = executeWithRetryAndFallback(agent, primaryProvider, step,
                    chunkSystem, chunkUser, missionId, maxTokens,
                    content -> isChunkOutputValid(content, chunkScenes), true);
            if (!response.isSuccess()) {
                throw new RuntimeException("Script chunk " + (i + 1) + "/" + chunks.size()
                        + " failed: " + response.getErrorMessage());
            }
            if (i < chunks.size() - 1) {
                throttle();
            }
            totalPrompt += response.getPromptTokens();
            totalCompletion += response.getCompletionTokens();
            totalLatency += response.getLatencyMs();

            JsonNode root = StrictJson.extractLastObject(response.getContent());
            if (root == null) {
                throw new RuntimeException("Script chunk " + (i + 1) + " returned no parseable JSON");
            }
            if (title == null && root.hasNonNull("title")) {
                title = root.get("title").asText();
            }
            JsonNode body = root.get("script_body");
            if (body == null || !body.isArray() || body.size() == 0) {
                throw new RuntimeException("Script chunk " + (i + 1) + " returned an empty script_body");
            }
            for (JsonNode scene : body) {
                ((ObjectNode) scene).put("scene_number", ++sceneCounter);
                allScenes.add(scene);
            }
            appendRunningSummary(runningSummary, body);
            log.info("Script chunk {}/{} produced {} scenes (total {})",
                    i + 1, chunks.size(), body.size(), sceneCounter);
            // Persist incremental progress for resume (so a FAILED at 13/14 can resume at 14 without re-running 13)
            try {
                ObjectNode progNode = objectMapper.createObjectNode();
                progNode.put("title", title != null ? title : "Untitled");
                progNode.set("script_body", allScenes);
                String progJson = objectMapper.writeValueAsString(progNode);
                step.setOutputData(progJson);
                step.setTotalTokens(totalPrompt + totalCompletion);
                step.setLatencyMs((int) totalLatency);
                executionService.saveStep(step.getExecutionId(), step);
            } catch (Exception e) {
                log.warn("Failed to persist script chunk progress {}/{}: {}", i + 1, chunks.size(), e.getMessage());
            }
        }

        ObjectNode merged = objectMapper.createObjectNode();
        merged.put("title", title != null ? title : "Untitled");
        merged.set("script_body", allScenes);
        String mergedJson = objectMapper.writeValueAsString(merged);

        step.setPromptTokens(totalPrompt);
        step.setCompletionTokens(totalCompletion);
        step.setTotalTokens(totalPrompt + totalCompletion);
        step.setLatencyMs((int) totalLatency);
        step.setPromptUsed("iterative script generation across " + chunks.size() + " chunks");

        if (!isOutputValidFor(agent, mergedJson)) {
            throw new RuntimeException("Merged iterative script failed validation with "
                    + allScenes.size() + " scenes");
        }
        log.info("Iterative script generation complete: {} scenes across {} chunks",
                allScenes.size(), chunks.size());
        return mergedJson;
    }

    private boolean isIterativeScriptMode(Agent agent) {
        if (agent == null || agent.getConfiguration() == null || agent.getConfiguration().isBlank()) {
            return false;
        }
        try {
            JsonNode config = objectMapper.readTree(agent.getConfiguration());
            return config.has("iterative") && config.get("iterative").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private int configScenesPerChunk(Agent agent) {
        if (agent == null || agent.getConfiguration() == null || agent.getConfiguration().isBlank()) {
            return 5;
        }
        try {
            JsonNode config = objectMapper.readTree(agent.getConfiguration());
            if (config.has("scenesPerChunk") && config.get("scenesPerChunk").isInt()) {
                int k = config.get("scenesPerChunk").asInt();
                return Math.max(2, Math.min(20, k));
            }
        } catch (Exception e) {
            // fall through to default
        }
        return 5;
    }

    /**
     * Pauses between chunked script calls so a burst of sequential LLM requests
     * stays under the provider's per-minute rate limit. Reads a configurable
     * delay from settings (video.chunk_delay_ms, default 4000).
     */
    private void throttle() {
        int delayMs = runtimeSettings.getInt(RuntimeSettings.KEY_VIDEO_CHUNK_DELAY_MS, 4000);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Extracts the raw content to divide into scenes: the research step's
     * structured output (sections array) when present, falling back to the raw
     * context text and any source documents for the mission.
     */
    private String extractResearchContent(String contextData, UUID missionId) {
        StringBuilder sb = new StringBuilder();

        if (contextData != null && !contextData.isBlank()) {
            JsonNode root = StrictJson.extractLastObject(contextData);
            if (root != null && root.has("sections") && root.get("sections").isArray()) {
                for (JsonNode s : root.get("sections")) {
                    if (s.hasNonNull("heading")) {
                        sb.append("## ").append(s.get("heading").asText()).append("\n");
                    }
                    if (s.hasNonNull("content")) {
                        sb.append(s.get("content").asText()).append("\n\n");
                    }
                }
            } else if (root != null && root.has("script_body")) {
                // research somehow returned a script; fall back to context text
                sb.append(contextData).append("\n");
            } else {
                sb.append(contextData).append("\n");
            }
        }

        if (missionId != null) {
            List<SourceDocument> docs = sourceDocumentRepository.findByMissionId(missionId);
            for (SourceDocument doc : docs) {
                if (doc.getContent() != null && !doc.getContent().isBlank()) {
                    sb.append("--- ").append(doc.getFilename()).append(" ---\n");
                    sb.append(doc.getContent()).append("\n\n");
                }
            }
        }

        if (sb.length() == 0) {
            return "Provide a complete, factual technical video on the mission topic.";
        }
        return sb.toString();
    }

    /**
     * Splits text into exactly n ordered chunks by proportional character length,
     * preferring paragraph boundaries near the target cut point. Even thin content
     * is split into n chunks so the full scene budget is always produced; the
     * model elaborates each slice into the requested number of scenes.
     */
    private List<String> splitContent(String text, int n) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            for (int i = 0; i < n; i++) {
                chunks.add("");
            }
            return chunks;
        }
        if (n <= 1) {
            chunks.add(text);
            return chunks;
        }
        int total = text.length();
        int target = Math.max(1, total / n);
        int start = 0;
        for (int i = 0; i < n; i++) {
            if (i == n - 1) {
                chunks.add(text.substring(start).trim());
                break;
            }
            int approx = Math.min(start + target, total);
            int cut = approx;
            int para = text.indexOf("\n\n", approx);
            if (para > 0 && para < approx + 400) {
                cut = para + 2;
            } else {
                int nl = text.indexOf('\n', approx);
                if (nl > 0 && nl < approx + 200) {
                    cut = nl + 1;
                }
            }
            if (cut <= start) {
                cut = Math.min(approx, total);
            }
            if (cut >= total) {
                chunks.add(text.substring(start).trim());
                break;
            }
            chunks.add(text.substring(start, cut).trim());
            start = cut;
        }
        while (chunks.size() < n) {
            chunks.add("");
        }
        return chunks;
    }

    private void appendRunningSummary(StringBuilder runningSummary, JsonNode body) {
        List<String> labels = new ArrayList<>();
        for (JsonNode scene : body) {
            String label = "";
            JsonNode spec = scene != null && scene.hasNonNull("visual_spec") ? scene.get("visual_spec") : null;
            if (spec != null && spec.hasNonNull("title")) {
                label = spec.get("title").asText();
            }
            if (label.isBlank() && scene.hasNonNull("speaker_text")) {
                String st = scene.get("speaker_text").asText();
                label = st.length() > 90 ? st.substring(0, 90) : st;
            }
            labels.add(label.isBlank() ? "(untitled)" : label);
        }
        runningSummary.append(String.join(" | ", labels)).append("\n");
    }

    /**
     * Light per-chunk validation: parseable JSON with a non-empty script_body of
     * at most maxScenes scenes, each with speaker_text and a visual_spec that
     * carries a non-empty components array.
     */
    private boolean isChunkOutputValid(String content, int maxScenes) {
        if (content == null || content.isBlank()) {
            return false;
        }
        try {
            JsonNode root = StrictJson.extractLastObject(content);
            if (root == null) {
                return false;
            }
            JsonNode body = root.get("script_body");
            if (body == null || !body.isArray() || body.size() == 0 || body.size() > maxScenes) {
                return false;
            }
            for (JsonNode scene : body) {
                // V2: visual_spec_v2 or semantic fields at scene level (objects/events/visualIntent)
                JsonNode spec = null;
                if (scene != null && scene.hasNonNull("visual_spec_v2") && scene.get("visual_spec_v2").isObject()) {
                    spec = scene.get("visual_spec_v2");
                } else if (scene != null && scene.hasNonNull("visual_spec") && scene.get("visual_spec").isObject()) {
                    spec = scene.get("visual_spec");
                    // If scene also has V2 top-level fields, consider them as part of spec (merged in MediaOrchestrationService)
                    // For validation, check if visual_spec has V2 semantic fields inside
                    if (scene.has("objects") || scene.has("events") || scene.has("visualIntent")) {
                        // V2 semantic at scene level — treat as valid if objects/events present
                        JsonNode objs = scene.get("objects");
                        JsonNode evts = scene.get("events");
                        if ((objs != null && objs.isArray() && objs.size() > 0) || (evts != null && evts.isArray() && evts.size() > 0)) {
                            if (!scene.hasNonNull("speaker_text") || scene.get("speaker_text").asText().isBlank()) return false;
                            continue;
                        }
                    }
                } else if (scene != null && scene.hasNonNull("diagram_spec") && scene.get("diagram_spec").isObject()) {
                    spec = scene.get("diagram_spec");
                }
                // Also allow pure V2 scene without visual_spec (objects/events directly)
                if (spec == null && scene != null && (scene.has("objects") || scene.has("events"))) {
                    JsonNode objs = scene.get("objects");
                    JsonNode evts = scene.get("events");
                    if ((objs != null && objs.isArray() && objs.size() > 0) || (evts != null && evts.isArray() && evts.size() > 0)) {
                        if (!scene.hasNonNull("speaker_text") && !scene.hasNonNull("narration")) return false;
                        continue;
                    }
                    return false;
                }
                if (spec == null || !spec.isObject()) {
                    return false;
                }
                // For V2 spec, check semanticObjects/objects or components
                JsonNode comps = spec.has("components") ? spec.get("components") : spec.get("shapes");
                JsonNode semObjs = spec.has("semanticObjects") ? spec.get("semanticObjects") : spec.get("objects");
                JsonNode semEvents = spec.has("semanticAnimations") ? spec.get("semanticAnimations") : spec.get("events");
                boolean hasLegacy = comps != null && comps.isArray() && comps.size() > 0;
                boolean hasV2 = (semObjs != null && semObjs.isArray() && semObjs.size() > 0) || (semEvents != null && semEvents.isArray() && semEvents.size() > 0) || spec.has("visualIntent") || spec.has("intent");
                if (!hasLegacy && !hasV2) {
                    return false;
                }
                if (!scene.hasNonNull("speaker_text") && !scene.hasNonNull("narration")) {
                    // V2 may use narration.text
                    JsonNode nar = scene.get("narration");
                    if (nar == null || !nar.hasNonNull("text") || nar.get("text").asText().isBlank()) return false;
                } else if (scene.hasNonNull("speaker_text") && scene.get("speaker_text").asText().isBlank()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildChunkSystemPrompt(Agent agent, Flow flow, UUID missionId, StoryTarget target,
                                          int scenesThisChunk, boolean isFirst, boolean isLast,
                                          int startSceneNumber) {
        StringBuilder sb = new StringBuilder();
        appendPromptBase(sb, agent, flow, missionId);
        sb.append("\n\nOUTPUT FORMAT REQUIREMENT (MANDATORY):\n")
          .append("You are writing ONE CHUNK of a larger video script. Respond with a single valid JSON object ")
          .append("(no markdown fences, no preamble). The per-scene visual_spec vocabulary is defined by your skill ")
          .append("(\"Video Script Generator\"), but you MUST now think in NARRATIVE BEATS, not sentences:\n")
          .append("- Each scene = one narrative beat with a clear visual intent (HOOK, EXPLAIN, ARCHITECTURE, CODE, SIMULATION, etc).\n")
          .append("- For each scene, provide: sceneId (kebab-case), narration {text}, visualIntent (e.g. REQUEST_INITIATION, ARCHITECTURE, CODE), ")
          .append("objects [{id,type}], events [{type:CREATE|TRAVEL|PUBLISH|HIGHLIGHT|FAIL, target, from, to, at}].\n")
          .append("- Also include the legacy visual_spec {layout,theme,title,components[],connections[],animation} — it remains the render contract; ")
          .append("  seed it from your objects/events (e.g., TRAVEL from mobile-app to api-gateway → connection).\n")
          .append("- Duration is VARIABLE: base on narration length + information density + visual complexity + animation needs, not a fixed 5-10s. ")
          .append("  Hook may be 3-5s, deep EXPLAIN may be 15-22s.\n")
          .append("This chunk covers scenes ").append(startSceneNumber).append(" to ")
          .append(startSceneNumber + scenesThisChunk - 1).append(" of the full script. Write EXACTLY ")
          .append(scenesThisChunk).append(" scenes. ")
          .append(isFirst
                ? "This is the OPENING chunk: scene " + startSceneNumber + " MUST be the hook that poses a concrete problem. "
                : "")
          .append(isLast
                ? "This is the FINAL chunk: end with a strong summary that counts what was solved (e.g. TODAY WE SOLVED 5 PROBLEMS: 01 ... 02 ...) and a call to action. "
                : "")
          .append("Use the raw content below as material; do not invent facts contradicting it. ")
          .append("STRICT CONTENT SAFETY RULES: family-friendly. ABSOLUTELY FORBIDDEN: violence, war, weapons, blood, gore, nudity, adult/sexual, drugs, alcohol.\n")
          .append("REPEAT: Output ONLY the raw JSON object with the requested ").append(scenesThisChunk)
          .append(" scenes. No markdown, no prose outside the JSON.");
        return sb.toString();
    }

    private String buildChunkUserMessage(Agent agent, UUID missionId, String chunkContent, String runningSummary,
                                         boolean isFirst, boolean isLast, int startSceneNumber, int scenesThisChunk) {
        StringBuilder sb = new StringBuilder();
        Mission mission = missionId != null ? missionRepository.findById(missionId).orElse(null) : null;
        if (mission != null) {
            sb.append("Mission topic: ").append(mission.getName() != null ? mission.getName() : "Untitled").append("\n");
            if (mission.getDescription() != null && !mission.getDescription().isBlank()) {
                sb.append("Mission brief: ").append(mission.getDescription()).append("\n");
            }
        }
        sb.append("\nYou must write scenes ").append(startSceneNumber).append(" to ")
          .append(startSceneNumber + scenesThisChunk - 1).append(" (exactly ").append(scenesThisChunk)
          .append(" short scenes).\n");
        if (runningSummary != null && !runningSummary.isBlank()) {
            sb.append("\nScenes already written (keep continuity):\n").append(runningSummary).append("\n");
        }
        sb.append("\nRaw content for THIS chunk:\n").append(chunkContent).append("\n");
        return sb.toString();
    }

    private void appendPromptBase(StringBuilder sb, Agent agent, Flow flow, UUID missionId) {
        Mission mission = missionId != null ? missionRepository.findById(missionId).orElse(null) : null;
        if (mission != null) {
            sb.append("Mission topic: ").append(mission.getName() != null ? mission.getName() : "Untitled").append("\n");
            if (mission.getDescription() != null && !mission.getDescription().isBlank()) {
                sb.append("Mission brief: ").append(mission.getDescription()).append("\n");
            }
            sb.append("Mission type: ").append(mission.getMissionType() != null ? mission.getMissionType() : "GENERAL").append("\n\n");
        }
        sb.append("You are the '").append(agent != null ? agent.getName() : "Unknown Agent")
          .append("' agent in a Karma OS video automation pipeline.\n");
        if (flow != null) {
            sb.append("Current flow: ").append(flow.getName())
              .append(" (").append(flow.getCategory()).append(")\n");
        }
        sb.append("\nRespond with well-structured content. Use Markdown formatting where appropriate.");
        List<Skill> skills = getAgentSkills(agent != null ? agent.getId() : null);
        if (!skills.isEmpty()) {
            sb.append("\n\nBehavioral contract from your assigned skills (MANDATORY, overrides any conflicting instruction above):\n");
            for (Skill s : skills) {
                sb.append("\n### Skill: ").append(s.getName()).append("\n");
                if (s.getContent() != null && !s.getContent().isBlank()) {
                    sb.append(s.getContent()).append("\n");
                } else {
                    sb.append(s.getDescription() != null ? s.getDescription() : "").append("\n");
                }
                String rules = s.getValidationRules();
                if (rules != null && !rules.isBlank() && !"{}".equals(rules.trim())) {
                    sb.append("Output validation rules: ").append(rules).append("\n");
                }
            }
        }
        String memoryMode = configuredMemoryMode(agent);
        if (!"OFF".equalsIgnoreCase(memoryMode)) {
            UUID ownerId = resolveMissionOwnerId(missionId);
            if (ownerId != null) {
                boolean includeAuto = "AUTO".equalsIgnoreCase(memoryMode);
                String memoryBlock = karmaMemoryService.buildMemoryPromptBlock(ownerId, includeAuto);
                if (!memoryBlock.isBlank()) {
                    sb.append("\n\n").append(memoryBlock);
                }
            }
        }
    }

    /**
     * Runs the LLM call with up to MAX_ATTEMPTS attempts, each time trying the
     * primary provider first and the agent's fallback provider second. The prompt
     * is augmented with a corrective instruction after a failed/empty attempt, so
     * the model gets a chance to produce a valid, non-stub response.
     */
    private LLMResponse executeWithRetryAndFallback(Agent agent, Provider primaryProvider, ExecutionStep step,
                                                    String systemPrompt, String userMessage, UUID missionId, int maxTokens) {
        return executeWithRetryAndFallback(agent, primaryProvider, step, systemPrompt, userMessage, missionId,
                maxTokens, content -> isOutputValidFor(agent, content), false);
    }

    /**
     * Public entry point so other services (e.g. MediaOrchestrationService's Director
     * step) can reuse the retry+fallback+corrective-hint machinery with their own
     * validator, without duplicating provider-chain/retry logic.
     */
    public LLMResponse executeWithRetryAndFallback(Agent agent, Provider primaryProvider, ExecutionStep step,
                                                    String systemPrompt, String userMessage, UUID missionId, int maxTokens,
                                                    Predicate<String> validator, boolean chunkMode) {
        final int maxAttempts = 3;
        LLMResponse lastResponse = null;
        String correctiveHint = "";

        Provider fallback = agent != null && agent.getFallbackProviderId() != null
            ? providerRepository.findById(agent.getFallbackProviderId()).orElse(null)
            : null;
        // Build chain: primary → fallback → all other CONNECTED LLM providers (to avoid single-provider 500/429/413)
        List<Provider> chainList = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        if (primaryProvider != null) {
            chainList.add(primaryProvider);
            seen.add(primaryProvider.getId());
        }
        if (fallback != null && !seen.contains(fallback.getId())) {
            chainList.add(fallback);
            seen.add(fallback.getId());
        }
        try {
            List<Provider> allConnected = providerRepository.findByStatus("CONNECTED").stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                    .filter(p -> isLlmProvider(p.getProviderType()))
                    .filter(p -> !seen.contains(p.getId()))
                    .filter(p -> {
                        try { return !providerKeyCipher.decrypt(p.getEncryptedApiKey()).isBlank(); } catch (Exception e) { return false; }
                    })
                    .sorted(Comparator.comparing(Provider::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            chainList.addAll(allConnected);
        } catch (Exception e) {
            log.debug("Failed to build extended provider chain: {}", e.getMessage());
        }
        Provider[] chain = chainList.toArray(new Provider[0]);
        // provider id + model that answered "overloaded"; later attempts switch to another model.
        Set<String> overloadedModels = new HashSet<>();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            for (Provider provider : chain) {
                LLMProvider llmProvider = resolveLLMProvider(provider);
                if (llmProvider == null || "mock".equals(llmProvider.getName())) {
                    continue;
                }
                String modelUsed = resolveModel(agent, provider, llmProvider);
                if (overloadedModels.contains(provider.getId() + ":" + modelUsed)) {
                    String alternative = alternativeModel(provider, overloadedModels);
                    if (alternative != null) {
                        log.info("Model {} is overloaded; trying {} on provider {}", modelUsed, alternative, provider.getName());
                        modelUsed = alternative;
                    }
                }
                LLMRequest request = new LLMRequest(systemPrompt, userMessage + correctiveHint);
                request.setModel(modelUsed);
                request.setTemperature(resolveTemperature(agent));
                request.setMaxTokens(maxTokens);
                applyGrounding(provider, request);

                step.setProviderId(provider.getId());
                step.setModelUsed(modelUsed);
                step.setPromptUsed(systemPrompt + "\n\n" + userMessage + correctiveHint);

                String hashKey = computeHash(modelUsed, systemPrompt, userMessage + correctiveHint, resolveTemperature(agent));
                LlmCache cached = llmCacheRepository.findById(hashKey).orElse(null);
                
                if (cached != null) {
                    log.info("LLM Cache hit for agent {} model {}", agent != null ? agent.getName() : "?", modelUsed);
                    lastResponse = LLMResponse.ok(cached.getResponseContent(), modelUsed, 0, 0, 0);
                } else {
                    lastResponse = llmProvider.chat(request);
                    if (lastResponse.isSuccess() && validator.test(lastResponse.getContent())) {
                        LlmCache newCache = new LlmCache();
                        newCache.setHashKey(hashKey);
                        newCache.setModel(modelUsed);
                        newCache.setResponseContent(lastResponse.getContent());
                        llmCacheRepository.save(newCache);
                    }
                }

                if (lastResponse.isSuccess() && validator.test(lastResponse.getContent())) {
                    if (attempt > 1 || provider != primaryProvider) {
                        log.info("Agent {} produced valid output on attempt {}/{} via provider {}",
                                agent != null ? agent.getName() : "?", attempt, maxAttempts, provider.getName());
                    }
                    return lastResponse;
                }

                String reason = lastResponse.isSuccess()
                    ? "output was empty, too short, or did not match the mandated schema: " + truncate(lastResponse.getContent(), 160)
                    : "provider error: " + lastResponse.getErrorMessage();
                log.warn("Agent {} attempt {}/{} via provider {} failed: {}",
                        agent != null ? agent.getName() : "?", attempt, maxAttempts, provider.getName(), reason);

                if (!lastResponse.isSuccess() && lastResponse.getErrorMessage() != null
                        && isOverloadError(lastResponse.getErrorMessage())) {
                    overloadedModels.add(provider.getId() + ":" + modelUsed);
                    boolean rateLimited = lastResponse.getErrorMessage().contains("429");
                    // 429s get their own backoff below; only wait here for 503-style overload.
                    if (!rateLimited && alternativeModel(provider, overloadedModels) == null && attempt < maxAttempts) {
                        long sleepTime = 15_000L * attempt;
                        log.warn("Provider {} overloaded with no alternative model; waiting {} ms before retry", provider.getName(), sleepTime);
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                if (!lastResponse.isSuccess() && lastResponse.getErrorMessage() != null) {
                    String err = lastResponse.getErrorMessage().toLowerCase();
                    if ((err.contains("429") || err.contains("rate limit") || err.contains("too many requests"))
                            && alternativeModel(provider, overloadedModels) == null) {
                        long sleepTime = (long) (30 * 1000 * attempt) + (long) (Math.random() * 5000);
                        log.warn("Rate limit hit, applying exponential backoff. Sleeping for {} ms before retry", sleepTime);
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                if (attempt >= maxAttempts) {
                    continue;
                }
            }

            if (attempt < maxAttempts) {
                if (isStoryScriptWriter(agent)) {
                    if (chunkMode) {
                        correctiveHint = "\n\nCORRECTIVE INSTRUCTION: Your previous response was rejected because it did NOT "
                            + "conform to the mandated chunk JSON schema. Respond with a SINGLE raw JSON object only "
                            + "(no markdown fences, no ```json, no preamble, no commentary) that has EXACTLY these top-level fields: "
                            + "{\"title\": \"...\", \"script_body\": [ {\"scene_number\": 1, \"speaker_text\": \"...\", "
                            + "\"visual_description\": \"...\", \"visual_spec\": { \"theme\": \"codeatcloud\", \"layout\": \"standard-content\", "
                            + "\"title\": \"...\", \"components\": [ "
                            + "{\"id\": \"a\", \"type\": \"heading\", \"label\": \"...\"} ] } } ] }. "
                            + "The \"script_body\" MUST be a non-empty JSON array of scene objects for THIS CHUNK ONLY; each scene MUST have "
                            + "\"scene_number\", \"speaker_text\" and a \"visual_spec\" object. Each \"visual_spec\" MUST have a non-empty "
                            + "\"components\" array; every component needs a unique \"id\" and one of the allowed \"type\" values from your skill. "
                            + "Write only the requested number of scenes for this chunk, numbering them consecutively from the start scene number "
                            + "provided. Output ONLY the raw JSON object.";
                    } else {
                        correctiveHint = "\n\nCORRECTIVE INSTRUCTION: Your previous response was rejected because it did NOT "
                            + "conform to the mandated JSON schema defined in your assigned skill (\"Video Script Generator\"). "
                            + "Respond with a SINGLE raw JSON object only (no markdown "
                            + "fences, no ```json, no preamble, no commentary) that has EXACTLY these top-level fields: "
                            + "{\"title\": \"...\", \"script_body\": [ {\"scene_number\": 1, \"speaker_text\": \"...\", "
                            + "\"visual_description\": \"...\", \"visual_spec\": { \"theme\": \"codeatcloud\", \"layout\": \"standard-content\", "
                            + "\"title\": \"...\", \"components\": [ "
                            + "{\"id\": \"a\", \"type\": \"heading\", \"label\": \"...\"} ] } } ] }. "
                            + "The \"script_body\" MUST be a non-empty JSON array of scene objects; each scene MUST have "
                            + "\"scene_number\", \"speaker_text\" and a \"visual_spec\" object. Each \"visual_spec\" MUST have a non-empty "
                            + "\"components\" array; every component needs a unique \"id\" and one of the allowed \"type\" values from your skill. "
                            + "Use ONLY the 13 allowed layouts; diagram layouts require a \"connections\" array, code-explainer requires a "
                            + "\"code\" component, and roadmap-timeline keeps at most 5 steps. "
                            + "Follow the layouts, themes and component vocabulary from your assigned skill. "
                            + "Do NOT output a content strategy, outline, plan, or any other JSON shape. Output ONLY the "
                            + "script_body JSON object.";
                    }
                } else {
                    correctiveHint = "\n\nCORRECTIVE INSTRUCTION: Your previous response was invalid (empty, too short, " +
                        "or it just stated that you lacked source material). Do NOT say you have no context or no " +
                        "source material. Produce complete, substantive, well-structured content for the requested " +
                        "deliverable using the mission topic provided. Respond with the full deliverable only.";
                }
            }
        }

        if (lastResponse == null) {
            log.warn("No usable provider for agent {}; returning error", agent != null ? agent.getName() : "?");
            return LLMResponse.error("No usable LLM provider available");
        }
        return lastResponse;
    }

    private LLMResponse handleMcpToolCalls(Agent agent, Provider primaryProvider, ExecutionStep step,
                                           String systemPrompt, String userMessage, UUID missionId,
                                           LLMResponse initial) {
        if (initial == null || !initial.isSuccess() || initial.getContent() == null) return initial;
        try {
            var catalog = mcpToolService.getToolsForAgent(agent.getId());
            if (catalog == null || catalog.isEmpty()) return initial;
            int totalTools = catalog.stream().mapToInt(c -> c.tools().size()).sum();
            if (totalTools == 0) return initial;
            var toolCall = mcpToolService.parseToolCall(initial.getContent());
            if (toolCall == null) return initial;
            log.info("MCP tool call detected from {}: {} on {}", agent.getName(), toolCall.tool(), toolCall.serverName());
            LLMResponse current = initial;
            StringBuilder conversation = new StringBuilder(userMessage);
            conversation.append("\n\n[LLM output]\n").append(current.getContent());
            for (int i = 0; i < 5; i++) {
                var call = mcpToolService.parseToolCall(current.getContent());
                if (call == null) break;
                JsonNode result;
                try {
                    result = mcpToolService.executeToolCall(call, catalog);
                } catch (Exception e) {
                    log.warn("MCP tool {} failed: {}", call.tool(), e.getMessage());
                    result = objectMapper.createObjectNode().put("error", e.getMessage());
                }
                String resultStr;
                try { resultStr = objectMapper.writeValueAsString(result); } catch (Exception e) { resultStr = result.toString(); }
                conversation.append("\n\n[Tool result for ").append(call.tool()).append("]\n").append(resultStr);
                conversation.append("\n\nContinue to produce the final deliverable using the tool result above. Do not call the same tool with identical arguments again.");
                LLMResponse next = executeWithRetryAndFallback(agent, primaryProvider, step, systemPrompt, conversation.toString(), missionId, 4096, content -> content != null && !content.isBlank(), false);
                if (next == null || !next.isSuccess()) break;
                // if next still contains a tool_call, loop; otherwise return it
                if (mcpToolService.parseToolCall(next.getContent()) == null) return next;
                current = next;
                conversation.append("\n\n[LLM output]\n").append(current.getContent());
            }
            return current;
        } catch (Exception e) {
            log.warn("MCP tool handling failed for {}: {}", agent.getName(), e.getMessage());
            return initial;
        }
    }

    private String computeHash(String model, String system, String user, double temp) {
        try {
            String payload = model + "|" + system + "|" + user + "|" + temp;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private void applyGrounding(Provider provider, LLMRequest request) {
        String type = provider != null && provider.getProviderType() != null
                ? provider.getProviderType().toLowerCase() : "";
        if (("google".equals(type) || "gemini".equals(type))
                && runtimeSettings.getBoolean(RuntimeSettings.KEY_GOOGLE_SEARCH_GROUNDING, false)) {
            request.setAdditionalParams(Map.of("googleSearchGrounding", true));
        }
    }

    /** Public so other services (e.g. the Director step) can reuse skill-content injection. */
    public String buildSystemPrompt(Agent agent, Flow flow, UUID missionId) {
        StringBuilder sb = new StringBuilder();

        Mission mission = missionId != null ? missionRepository.findById(missionId).orElse(null) : null;
        if (mission != null) {
            sb.append("Mission topic: ").append(mission.getName() != null ? mission.getName() : "Untitled").append("\n");
            if (mission.getDescription() != null && !mission.getDescription().isBlank()) {
                sb.append("Mission brief: ").append(mission.getDescription()).append("\n");
            }
            sb.append("Mission type: ").append(mission.getMissionType() != null ? mission.getMissionType() : "GENERAL").append("\n\n");
        }

        boolean mandatedFormat = false;
        if (agent != null && agent.getDefaultPromptId() != null) {
            Prompt prompt = promptRepository.findById(agent.getDefaultPromptId()).orElse(null);
            if (prompt != null) {
                sb.append(prompt.getContent()).append("\n\n");
                String pc = prompt.getContent().toLowerCase();
                mandatedFormat = pc.contains("return valid json") || pc.contains("output format")
                        || pc.contains("exact structure") || pc.contains("json schema");
            }
        }

        sb.append("You are the '").append(agent != null ? agent.getName() : "Unknown Agent")
          .append("' agent in a Karma OS video automation pipeline.\n");

        if (flow != null) {
            sb.append("Current flow: ").append(flow.getName())
              .append(" (").append(flow.getCategory()).append(")\n");
        }

        sb.append("\nRespond with well-structured content. Use Markdown formatting where appropriate.");
        if (mandatedFormat) {
            sb.append("\nIMPORTANT: If the prompt above mandates a specific output format (e.g. a JSON schema), "
                    .concat("you MUST follow it exactly. Do not switch to Markdown prose or discuss your reasoning in the output. ")
                    .concat("Produce ONLY the required structured output."));
        }

        List<Skill> skills = getAgentSkills(agent != null ? agent.getId() : null);
        if (!skills.isEmpty()) {
            sb.append("\n\nBehavioral contract from your assigned skills (MANDATORY, overrides any conflicting instruction above):\n");
            for (Skill s : skills) {
                sb.append("\n### Skill: ").append(s.getName()).append("\n");
                if (s.getContent() != null && !s.getContent().isBlank()) {
                    sb.append(s.getContent()).append("\n");
                } else {
                    sb.append(s.getDescription() != null ? s.getDescription() : "").append("\n");
                }
                String rules = s.getValidationRules();
                if (rules != null && !rules.isBlank() && !"{}".equals(rules.trim())) {
                    sb.append("Output validation rules: ").append(rules).append("\n");
                }
            }
        }

        String memoryMode = configuredMemoryMode(agent);
        if (!"OFF".equalsIgnoreCase(memoryMode)) {
            UUID ownerId = resolveMissionOwnerId(missionId);
            if (ownerId != null) {
                boolean includeAuto = "AUTO".equalsIgnoreCase(memoryMode);
                String memoryBlock = karmaMemoryService.buildMemoryPromptBlock(ownerId, includeAuto);
                if (!memoryBlock.isBlank()) {
                    sb.append("\n\n").append(memoryBlock);
                }
            }
        }

        if (isStoryScriptWriter(agent)) {
            StoryTarget target = configuredStoryTarget(agent, missionId);
            sb.append("\n\nOUTPUT FORMAT REQUIREMENT (MANDATORY):\n")
              .append("Respond with a single valid JSON object (no markdown fences, no preamble, no commentary). ")
              .append("Your output must be raw JSON only. The exact JSON schema, per-scene visual spec, allowed layouts, ")
              .append("themes, and component vocabulary are defined by the behavioral contract of your assigned skill above ")
              .append("(\"Video Script Generator\"). The skill is the SINGLE source of truth for the schema — do not invent a ")
              .append("different shape. Every scene MUST carry the skill's per-scene visual spec object with a non-empty ")
              .append("components array; each component needs a unique \"id\".\n")
              .append("You MUST write a full-length script that assembles into a target video of roughly ")
              .append(target.targetDurationSeconds).append(" seconds. Write exactly ").append(target.minScenes)
              .append(" to ").append(target.maxScenes).append(" scenes. ")
              .append("Each speaker_text must be a complete spoken paragraph of about ")
              .append(target.wordsPerScene)
              .append("-").append(target.wordsPerScene + 2 * SCENE_WORD_SLACK)
              .append(" words (spoken at ~").append(WORDS_PER_SECOND).append(" words per second, so each scene is roughly ")
              .append(Math.round(target.wordsPerScene / WORDS_PER_SECOND)).append("-").append(Math.round((target.wordsPerScene + 2 * SCENE_WORD_SLACK) / WORDS_PER_SECOND))
              .append(" seconds). ")
              .append("Make the script complete and non-branded. ")
              // Retention rules: viewers decide in the first seconds, and generic slides read as templated.
              .append("HOOK: scene 1 must NOT use a title-slide or section-header layout; show the problem itself on screen ")
              .append("(e.g. a terminal-walkthrough with the real error output, a before/after comparison, or a big-number stat) ")
              .append("and open the narration on that problem, not on the topic name. ")
              .append("NARRATION: never write list numbering such as \"01\", \"02\" or \"step 1:\" in speaker_text (it is read aloud), ")
              .append("and the recap/outro may only mention concepts, commands and tools actually explained in earlier scenes. ")
              .append("TECH ICONS: whenever a diagram node represents a real technology (Docker, Linux, PostgreSQL, Redis, Kafka, ")
              .append("Kubernetes, Node.js, Nginx, AWS, ...), set \"data\": {\"tech\": \"<name>\"} on it so its real logo is shown. ")
              .append("STRICT CONTENT SAFETY RULES: The content must be family-friendly, age-appropriate and suitable for all audiences. ")
              .append("ABSOLUTELY FORBIDDEN: any violence, fighting, war, weapons, guns, swords, blood, gore, injury, death, killing, ")
              .append("fear, horror, nudity, adult/sexual content, drugs, alcohol, smoking, or anything inappropriate. ")
              .append("Follow the behavioral contract described in the skill above for style, tone and diagram rules.\n")
              .append("REPEAT: Output ONLY the raw JSON object described by your skill. Do NOT include markdown headings, bullet lists, ")
              .append("explanatory prose, chain-of-thought reasoning, or any text outside the JSON.\n\n")
              // YouTube retention hook + chapter metadata
              .append("YOUTUBE RETENTION DIRECTIVES (CRITICAL for watch-through rate):\n")
              .append("1. HOOK (Scene 1, ≤5 seconds): Open with a dramatic question, surprising statistic, or bold claim ")
              .append("that creates curiosity. Use layout 'big-number' or 'title-slide'. Example speaker_text: ")
              .append("\"Did you know that 90% of production outages are caused by just three patterns? ")
              .append("Today, we'll break down exactly how to prevent them.\"\n")
              .append("2. ROADMAP (Scene 2): Briefly tease the agenda — \"In this video, you'll learn...\" — ")
              .append("so viewers know what value they'll get and stay through the whole video.\n")
              .append("3. CTA (Final Scene): End with a clear call to action — ask viewers to subscribe, ")
              .append("like, or comment with a specific question related to the topic.\n")
              .append("4. CHAPTERS: Each scene MUST include a \"chapter_title\" field (short, 3-6 words, ")
              .append("suitable for YouTube chapter timestamps). Example: \"Why Race Conditions Kill\", ")
              .append("\"The Fix: Mutex Locks\", \"Performance Benchmarks\".\n");
        }

        return sb.toString();
    }

    private boolean isStoryScriptWriter(Agent agent) {
        if (agent == null || agent.getName() == null) return false;
        String n = agent.getName().toLowerCase();
        String c = agent.getCategory() != null ? agent.getCategory().toLowerCase() : "";
        return n.contains("script") || n.contains("story") || n.contains("narrat")
            || c.contains("script") || c.contains("story");
    }

    /**
     * Validates an LLM step's output. For story/script agents the output must be a
     * raw JSON object whose top-level "script_body" is a non-empty array, otherwise
     * the response is treated as invalid and the retry loop corrects it.
     */
    /**
     * Enforces the video renderer contract on a scene spec:
     * - layout must be one of the 13 implemented layouts (missing -> allowed, coerced to standard-content downstream)
     * - every component must use a type from the video vocabulary
     * - per-layout cardinality/requirement rules
     */
    private boolean isSpecCompliant(JsonNode spec) {
        // V2: if semantic, skip V1 strict checks — V2 has its own validation via semanticAnimations/objects
        if (spec.has("schemaVersion") && "v2".equals(spec.path("schemaVersion").asText())) return true;
        if (spec.has("visualIntent") || spec.has("semanticObjects") || spec.has("semanticAnimations")) return true;
        JsonNode layoutNode = spec.get("layout");
        String layout = "";
        if (layoutNode != null && layoutNode.isTextual()) {
            layout = layoutNode.asText();
            if (!ALLOWED_LAYOUTS.contains(layout)) {
                log.info("Script output rejected: layout \"{}\" is not implemented by the video renderer", layout);
                return false;
            }
        }
        JsonNode comps = spec.get("components");
        if (comps == null || !comps.isArray()) {
            // V2 without legacy components but with semantic objects is compliant
            if (spec.has("semanticObjects") || spec.has("objects")) return true;
            return false;
        }
        for (JsonNode comp : comps) {
            if (!comp.isObject()) {
                return false;
            }
            String type = comp.hasNonNull("type") ? comp.get("type").asText() : null;
            if (type == null || !ALLOWED_COMPONENT_TYPES.contains(type)) {
                log.warn("Coercing unknown component type \"{}\" to \"card\" in layout \"{}\"", type, layout);
                if (comp instanceof ObjectNode) {
                    ((ObjectNode) comp).put("type", "card");
                }
            }
        }
        int n = comps.size();
        switch (layout) {
            case "code-explainer": {
                boolean hasCode = false;
                for (JsonNode comp : comps) {
                    if ("code".equals(comp.path("type").asText())) {
                        hasCode = true;
                        break;
                    }
                }
                if (!hasCode) {
                    log.warn("Coercing code-explainer without code — injecting placeholder code component");
                    if (comps instanceof ArrayNode) {
                        ObjectNode placeholder = objectMapper.createObjectNode();
                        placeholder.put("id", "auto-code-" + System.nanoTime());
                        placeholder.put("type", "code");
                        placeholder.put("label", "Example");
                        ((ArrayNode) comps).add(placeholder);
                        n = comps.size();
                    } else {
                        log.info("Script output rejected: code-explainer requires a \"code\" component");
                        return false;
                    }
                }
                break;
            }
            case "architecture-diagram": {
                JsonNode conns = spec.get("connections");
                if (conns == null || !conns.isArray() || conns.size() == 0) {
                    log.warn("Coercing architecture-diagram without connections — injecting default connection");
                    if (spec instanceof ObjectNode && comps instanceof ArrayNode && comps.size() >= 2) {
                        String from = comps.get(0).path("id").asText("a");
                        String to = comps.get(1).path("id").asText("b");
                        ArrayNode arr = objectMapper.createArrayNode();
                        ObjectNode c = objectMapper.createObjectNode();
                        c.put("from", from);
                        c.put("to", to);
                        arr.add(c);
                        ((ObjectNode) spec).set("connections", arr);
                    } else {
                        log.info("Script output rejected: architecture-diagram requires a non-empty \"connections\" array");
                        return false;
                    }
                }
                break;
            }
            case "roadmap-timeline":
                if (n > 5) {
                    log.warn("Coercing roadmap-timeline from {} to 5 steps", n);
                    if (comps instanceof ArrayNode) {
                        while (comps.size() > 5) ((ArrayNode) comps).remove(5);
                        n = comps.size();
                    } else {
                        log.info("Script output rejected: roadmap-timeline exceeds 5 steps");
                        return false;
                    }
                }
                break;
            case "feature-grid":
                if (n > 8) {
                    log.warn("Coercing feature-grid from {} to 8 components", n);
                    if (comps instanceof ArrayNode) { while (comps.size() > 8) ((ArrayNode) comps).remove(8); } else return false;
                }
                break;
            case "big-number":
                if (n > 6) {
                    log.warn("Coercing big-number from {} to 6 components", n);
                    if (comps instanceof ArrayNode) { while (comps.size() > 6) ((ArrayNode) comps).remove(6); } else return false;
                }
                break;
            case "terminal-walkthrough":
                if (n > 10) {
                    log.warn("Coercing terminal-walkthrough from {} to 10", n);
                    if (comps instanceof ArrayNode) { while (comps.size() > 10) ((ArrayNode) comps).remove(10); } else return false;
                }
                break;
            case "bento":
            case "dashboard":
                if (n > 12) {
                    log.warn("Coercing {} from {} to 12 components", layout, n);
                    if (comps instanceof ArrayNode) { while (comps.size() > 12) ((ArrayNode) comps).remove(12); } else return false;
                }
                break;
            case "infographic":
                if (n > 16) {
                    log.warn("Coercing infographic from {} to 16", n);
                    if (comps instanceof ArrayNode) { while (comps.size() > 16) ((ArrayNode) comps).remove(16); } else return false;
                }
                break;
            default:
                break;
        }
        return true;
    }

    public boolean isOutputValidFor(Agent agent, String content) {
        if (!StepContextDistiller.isValidOutput(content)) {
            return false;
        }
        if (isStoryScriptWriter(agent)) {
            try {
                JsonNode root = StrictJson.extractLastObject(content);
                if (root == null) {
                    return false;
                }
                JsonNode body = root.get("script_body");
                if (body == null || !body.isArray() || body.size() == 0) {
                    return false;
                }
                StoryTarget target = configuredStoryTarget(agent, null);
                int softFloor = Math.max(1, Math.min(5, target.minScenes()));
                if (body.size() < softFloor) {
                    log.info("Script output rejected: {} scenes found, soft floor is {}", body.size(), softFloor);
                    return false;
                }
                for (JsonNode scene : body) {
                    JsonNode spec = null;
                    // V2: visual_spec_v2 first
                    if (scene != null && scene.hasNonNull("visual_spec_v2") && scene.get("visual_spec_v2").isObject()) {
                        spec = scene.get("visual_spec_v2");
                    } else if (scene != null && scene.hasNonNull("visual_spec") && scene.get("visual_spec").isObject()) {
                        spec = scene.get("visual_spec");
                        // If scene has V2 top-level semantic fields, treat as V2 (merged)
                        if (scene.has("objects") || scene.has("events") || scene.has("visualIntent")) {
                            JsonNode objs = scene.get("objects");
                            JsonNode evts = scene.get("events");
                            if ((objs != null && objs.isArray() && objs.size() > 0) || (evts != null && evts.isArray() && evts.size() > 0) || scene.has("visualIntent")) {
                                continue; // V2 semantic at scene level valid
                            }
                        }
                    } else if (scene != null && scene.hasNonNull("diagram_spec") && scene.get("diagram_spec").isObject()) {
                        spec = scene.get("diagram_spec");
                    } else if (scene != null && (scene.has("objects") || scene.has("events"))) {
                        // Pure V2 without visual_spec
                        JsonNode objs = scene.get("objects");
                        JsonNode evts = scene.get("events");
                        if ((objs != null && objs.isArray() && objs.size() > 0) || (evts != null && evts.isArray() && evts.size() > 0)) continue;
                        return false;
                    }
                    if (spec == null || !spec.isObject()) {
                        return false;
                    }
                    JsonNode shapes = spec.has("components") ? spec.get("components") : spec.get("shapes");
                    JsonNode semObjs = spec.has("semanticObjects") ? spec.get("semanticObjects") : spec.get("objects");
                    JsonNode blocks = spec.get("content_blocks");
                    boolean hasV2 = (semObjs != null && semObjs.isArray() && semObjs.size() > 0) || spec.has("visualIntent") || spec.has("intent") || (spec.has("semanticAnimations") || spec.has("events"));
                    if ((shapes == null || !shapes.isArray() || shapes.size() == 0) &&
                        (blocks == null || !blocks.isArray() || blocks.size() == 0) && !hasV2) {
                        return false;
                    }
                    if (shapes != null && shapes.isArray()) {
                        for (JsonNode shape : shapes) {
                            if (shape == null || !shape.isObject()) {
                                return false;
                            }
                        }
                    }
                    if (blocks != null && blocks.isArray()) {
                        for (JsonNode block : blocks) {
                            if (block == null || !block.hasNonNull("heading") || !block.hasNonNull("text")) {
                                return false;
                            }
                        }
                    }
                    if (shapes != null && shapes.isArray() && shapes.size() > 0 && !isSpecCompliant(spec)) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private StoryTarget configuredStoryTarget(Agent agent, UUID missionId) {
        int targetDuration = runtimeSettings.getInt(RuntimeSettings.KEY_VIDEO_TARGET_DURATION, 900);
        int minScenes = runtimeSettings.getInt(RuntimeSettings.KEY_VIDEO_MIN_SCENES, 30);
        int maxScenes = runtimeSettings.getInt(RuntimeSettings.KEY_VIDEO_MAX_SCENES, 60);
        int wordsPerScene = runtimeSettings.getInt(RuntimeSettings.KEY_VIDEO_WORDS_PER_SCENE, DEFAULT_WORDS_PER_SCENE);

        if (agent != null && agent.getConfiguration() != null && !agent.getConfiguration().isBlank()) {
            try {
                JsonNode config = objectMapper.readTree(agent.getConfiguration());
                if (config.has("targetDurationSeconds") && config.get("targetDurationSeconds").isInt()) {
                    targetDuration = Math.max(30, config.get("targetDurationSeconds").asInt());
                }
                if (config.has("minScenes") && config.get("minScenes").isInt()) {
                    minScenes = Math.max(1, config.get("minScenes").asInt());
                }
                if (config.has("maxScenes") && config.get("maxScenes").isInt()) {
                    maxScenes = Math.max(minScenes, config.get("maxScenes").asInt());
                }
                if (config.has("wordsPerScene") && config.get("wordsPerScene").isInt()) {
                    wordsPerScene = Math.max(10, config.get("wordsPerScene").asInt());
                }
            } catch (Exception ignored) {
            }
        }

        if (missionId != null) {
            try {
                Mission mission = missionRepository.findById(missionId).orElse(null);
                if (mission != null && mission.getTargetDurationSeconds() != null && mission.getTargetDurationSeconds() > 0) {
                    targetDuration = Math.max(30, mission.getTargetDurationSeconds());
                    // The scene limits default to a 15-minute video (30-36 scenes). Size them from the
                    // mission's own duration, otherwise a "4 minute" video came out at ~8 minutes.
                    int scenes = Math.max(3, (int) Math.round(targetDuration * WORDS_PER_SECOND / wordsPerScene));
                    minScenes = Math.min(minScenes, scenes);
                    maxScenes = Math.max(minScenes, Math.min(maxScenes, scenes + 1));
                }
            } catch (Exception ignored) {
            }
        }

        return new StoryTarget(targetDuration, minScenes, maxScenes, wordsPerScene);
    }

    /** Narration pace used to size scenes, measured on Edge TTS output (1,202 words -> 448s). */
    private static final double WORDS_PER_SECOND = 2.6;
    /**
     * Words per scene. Models write ~40-45 words per scene whatever they are asked (a 70-word
     * target produced 45), and 15-20s per visual keeps retention up, so plan around that.
     */
    private static final int DEFAULT_WORDS_PER_SCENE = 45;
    /** Allowed overshoot per scene above wordsPerScene, kept small so the total length holds. */
    private static final int SCENE_WORD_SLACK = 8;

    private record StoryTarget(int targetDurationSeconds, int minScenes, int maxScenes, int wordsPerScene) {
        StoryTarget {
            if (targetDurationSeconds <= 0) targetDurationSeconds = 900;
            if (minScenes <= 0) minScenes = 30;
            if (maxScenes < minScenes) maxScenes = minScenes;
            if (wordsPerScene <= 0) wordsPerScene = DEFAULT_WORDS_PER_SCENE;
        }
    }

    private String configuredMemoryMode(Agent agent) {
        if (agent == null || agent.getConfiguration() == null) return "OFF";
        try {
            JsonNode config = objectMapper.readTree(agent.getConfiguration());
            if (config.has("memoryMode")) {
                String mode = config.get("memoryMode").asText();
                return mode == null || mode.isBlank() ? "OFF" : mode.trim().toUpperCase();
            }
        } catch (Exception ignored) {}
        return "OFF";
    }

    private UUID resolveMissionOwnerId(UUID missionId) {
        try {
            Mission mission = missionId != null ? missionRepository.findById(missionId).orElse(null) : null;
            if (mission == null || mission.getProjectId() == null) return null;
            Project project = projectRepository.findById(mission.getProjectId()).orElse(null);
            if (project == null || project.getWorkspaceId() == null) return null;
            Workspace workspace = workspaceRepository.findById(project.getWorkspaceId()).orElse(null);
            return workspace != null ? workspace.getOwnerId() : null;
        } catch (Exception e) {
            log.warn("Failed to resolve mission owner for memory: {}", e.getMessage());
            return null;
        }
    }

    private String buildUserMessage(Agent agent, String contextData, UUID missionId, String reviewerFeedback) {
        StringBuilder sb = new StringBuilder();

        Mission mission = missionId != null ? missionRepository.findById(missionId).orElse(null) : null;
        String missionTopic = mission != null
            ? "Mission topic: " + (mission.getName() != null ? mission.getName() : "")
              + (mission.getDescription() != null && !mission.getDescription().isBlank()
                    ? " - " + mission.getDescription() : "")
            : "";

        boolean hasContext = contextData != null && !contextData.isBlank();
        if (hasContext) {
            sb.append("Context from previous steps:\n").append(contextData).append("\n\n");
        }

        if (!hasContext && missionTopic.isBlank()) {
            sb.append("No upstream context was provided and no mission topic is available.\n")
              .append("Produce your best possible output based on your role description alone.\n\n");
        } else if (!hasContext) {
            sb.append("Work from the mission topic directly (no upstream step produced context):\n")
              .append(missionTopic).append("\n\n");
        }

        List<SourceDocument> docs = missionId != null ? sourceDocumentRepository.findByMissionId(missionId) : List.of();
        if (!docs.isEmpty()) {
            sb.append("Source documents for this mission:\n");
            for (SourceDocument doc : docs) {
                sb.append("--- ").append(doc.getFilename()).append(" ---\n");
                sb.append(doc.getContent()).append("\n\n");
            }
        }

        if (agent != null) {
            sb.append("Instructions for ").append(agent.getName()).append(":\n");
            sb.append("Process the above context and produce the required output for this step.\n");
        }

        if (reviewerFeedback != null && !reviewerFeedback.isBlank()) {
            sb.append("\n\nREVIEWER FEEDBACK ON YOUR PREVIOUS ATTEMPT:\n").append(reviewerFeedback)
              .append("\nPlease revise your output accordingly and address every point above.\n");
        }

        return sb.toString();
    }

    /**
     * Applies the frontend-configurable per-call token budget. Heavy deliverable
     * steps (planning, content, review) use the reasoning tier; lighter steps use
     * the default tier.
     */
    private int resolveMaxTokens(Flow flow, Agent agent, UUID missionId) {
        String category = flow != null && flow.getCategory() != null
                ? flow.getCategory().toUpperCase(Locale.ROOT) : "";
        boolean reasoningTier = "PLANNING".equals(category)
                || "CONTENT".equals(category)
                || "REVIEW".equals(category);
        int reasoning = reasoningTier
                ? runtimeSettings.getInt(RuntimeSettings.KEY_MAX_TOKENS_REASONING, 4096)
                : runtimeSettings.getInt(RuntimeSettings.KEY_MAX_TOKENS_DEFAULT, 1024);
        if (isStoryScriptWriter(agent)) {
            int modelOutputCap = runtimeSettings.getInt(RuntimeSettings.KEY_MAX_TOKENS_SCRIPT, 8192);
            StoryTarget target = configuredStoryTarget(agent, missionId);
            int needed = Math.max(target.maxScenes(), target.minScenes()) * 220;
            if (modelOutputCap > reasoning) {
                log.info("Story script target {} scenes needs ~{} tokens; raising budget from {} to {} (model output cap)",
                        Math.max(target.maxScenes(), target.minScenes()), needed, reasoning, modelOutputCap);
                return modelOutputCap;
            }
        }
        return reasoning;
    }

    private double resolveTemperature(Agent agent) {
        if (agent == null || agent.getConfiguration() == null) return 0.7;
        try {
            JsonNode config = objectMapper.readTree(agent.getConfiguration());
            if (config.has("temperature")) {
                return config.get("temperature").asDouble();
            }
        } catch (Exception ignored) {}
        return 0.7;
    }

    private Provider resolveProvider(Agent agent) {
        if (agent == null) return null;
        UUID providerId = agent.getDefaultProviderId();
        if (providerId != null) {
            Provider p = providerRepository.findById(providerId).orElse(null);
            if (p != null && p.getDeletedAt() == null && !"PUBLISHING".equalsIgnoreCase(p.getProviderType())) {
                log.info("Resolved default provider {} for agent {}", p.getProviderType(), agent.getName());
                return p;
            }
        }
        List<Provider> providers = providerRepository.findByStatus("CONNECTED").stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                .filter(p -> !providerKeyCipher.decrypt(p.getEncryptedApiKey()).isBlank())
                .sorted(Comparator.comparing(Provider::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (!providers.isEmpty()) {
            Provider p = providers.get(0);
            log.info("No default provider for agent {}, using first connected provider with a key: {}", agent.getName(), p.getProviderType());
            return p;
        }
        log.warn("No configured provider found for agent {}, execution will fall back to mock", agent.getName());
        return null;
    }

    private LLMProvider resolveLLMProvider(Provider provider) {
        if (provider == null) {
            log.warn("Resolving mock LLM provider: no configured AI provider available");
            return providerFactory.getFallbackProvider();
        }
        try {
            String apiKey = providerKeyCipher.decrypt(provider.getEncryptedApiKey());
            return providerFactory.getProvider(provider.getProviderType(), apiKey, provider.getApiEndpoint());
        } catch (Exception e) {
            log.warn("Failed to create provider for type={}, using mock", provider.getProviderType(), e);
            return providerFactory.getFallbackProvider();
        }
    }

    /** Transient capacity errors (HTTP 503 / "high demand" / "overloaded"), distinct from bad requests. */
    private static boolean isOverloadError(String message) {
        String m = message.toLowerCase();
        return m.contains("503") || m.contains("service unavailable") || m.contains("overloaded")
                || m.contains("high demand") || m.contains("unavailable")
                // Quota/rate limits are per model (e.g. Gemini free tier: 20 requests/day/model),
                // so another model on the same key usually still works.
                || m.contains("429") || m.contains("too many requests") || m.contains("quota");
    }

    /** Next configured text model on the provider that has not been reported overloaded, or null. */
    private String alternativeModel(Provider provider, Set<String> overloadedModels) {
        if (provider.getModels() == null || provider.getModels().isBlank()) return null;
        try {
            List<String> models = objectMapper.readValue(provider.getModels(), new TypeReference<List<String>>() {});
            for (String model : models) {
                if (com.karmaos.provider.ModelRanker.isTextChatModel(model)
                        && !overloadedModels.contains(provider.getId() + ":" + model)) {
                    return model;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveModel(Agent agent, Provider provider, LLMProvider llmProvider) {
        String configured = configuredModel(agent);
        // Provider-aware: only use configured model if it belongs to the current provider's catalog;
        // otherwise the fallback provider would send an incompatible model name (e.g. nemotron to Gemini → 404).
        if (configured != null && !configured.isBlank() && provider != null
                && provider.getModels() != null && !provider.getModels().isBlank()) {
            try {
                List<String> models = objectMapper.readValue(provider.getModels(), new TypeReference<List<String>>() {});
                if (models != null && !models.isEmpty()) {
                    boolean belongs = models.stream().anyMatch(m -> m.equalsIgnoreCase(configured));
                    if (belongs) return configured;
                    // configured belongs to a different provider → use this provider's default
                    return models.get(0);
                }
            } catch (Exception ignored) {}
            // provider has no parseable catalog — still prefer configured only if provider type matches
            return configured;
        }
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (provider != null && provider.getModels() != null && !provider.getModels().isBlank()) {
            try {
                List<String> models = objectMapper.readValue(provider.getModels(), new TypeReference<List<String>>() {});
                if (models != null && !models.isEmpty()) {
                    return models.get(0);
                }
            } catch (Exception ignored) {}
        }
        return llmProvider.getDefaultModel();
    }

    private String configuredModel(Agent agent) {
        if (agent == null || agent.getConfiguration() == null) return null;
        try {
            JsonNode config = objectMapper.readTree(agent.getConfiguration());
            if (config.has("model")) {
                String model = config.get("model").asText();
                return model == null || model.isBlank() ? null : model.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<Skill> getAgentSkills(UUID agentId) {
        if (agentId == null) return List.of();
        try {
            return agentSkillRepository.findByIdAgentId(agentId).stream()
                .map(as -> skillRepository.findById(as.getId().getSkillId()).orElse(null))
                .filter(s -> s != null && s.getDeletedAt() == null && isLiveSkillStatus(s.getStatus()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean isLiveSkillStatus(String status) {
        return status != null && ("ACTIVE".equals(status) || "INSTALLED".equals(status));
    }

    public Map<String, Object> executePromptSync(String systemPrompt, String userMessage) {
        try {
            LLMRequest request = new LLMRequest(systemPrompt, userMessage);

            for (Provider provider : resolveProviderChain()) {
                LLMProvider llmProvider = resolveLLMProvider(provider);
                if (llmProvider == null || "mock".equals(llmProvider.getName())) {
                    continue;
                }
                request.setModel(resolveModel(null, provider, llmProvider));
                applyGrounding(provider, request);
                LLMResponse response = llmProvider.chat(request);
                if (response.isSuccess()) {
                    log.info("executePromptSync succeeded via provider {} model {}", provider.getName(), response.getModel());
                    return Map.of(
                        "content", response.getContent(),
                        "model", response.getModel(),
                        "tokens", response.getTotalTokens(),
                        "success", true,
                        "error", "",
                        "provider", provider.getName()
                    );
                }
                log.warn("Provider {} failed for sync prompt: {}", provider.getName(), response.getErrorMessage());
            }

            return Map.of(
                "content", "",
                "model", "",
                "tokens", 0,
                "success", false,
                "error", "All providers failed to process the request"
            );
        } catch (Exception e) {
            log.error("executePromptSync error", e);
            return Map.of(
                "content", "",
                "model", "",
                "tokens", 0,
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
            );
        }
    }

    private List<Provider> resolveProviderChain() {
        List<Provider> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        try {
            Optional<Agent> karmaAi = agentRepository.findByNameContainingIgnoreCase("Karma AI")
                    .stream().filter(a -> "ACTIVE".equals(a.getStatus())).findFirst();
            if (karmaAi.isPresent()) {
                addProviderCandidate(result, seen, karmaAi.get().getDefaultProviderId());
                addProviderCandidate(result, seen, karmaAi.get().getFallbackProviderId());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve Karma AI default provider", e);
        }

        for (Provider p : providerRepository.findByStatus("CONNECTED")) {
            if (p.getDeletedAt() == null
                    && isLlmProvider(p.getProviderType())
                    && !providerKeyCipher.decrypt(p.getEncryptedApiKey()).isBlank()
                    && !seen.contains(p.getId())) {
                result.add(p);
                seen.add(p.getId());
            }
        }
        return result;
    }

    private void addProviderCandidate(List<Provider> result, Set<UUID> seen, UUID providerId) {
        if (providerId == null || seen.contains(providerId)) return;
        providerRepository.findById(providerId).ifPresent(p -> {
            if ("CONNECTED".equals(p.getStatus())
                    && p.getDeletedAt() == null
                    && isLlmProvider(p.getProviderType())
                    && !providerKeyCipher.decrypt(p.getEncryptedApiKey()).isBlank()) {
                result.add(p);
                seen.add(p.getId());
            }
        });
    }

    private boolean isLlmProvider(String providerType) {
        if (providerType == null) return false;
        String t = providerType.toLowerCase();
        return t.equals("google") || t.equals("gemini") || t.equals("openrouter")
            || t.equals("opencode") || t.equals("groq");
    }

    private BigDecimal calculateCost(String model, int promptTokens, int completionTokens) {
        Double rate = COST_PER_TOKEN.getOrDefault(model, 1.0e-6);
        double cost = (promptTokens + completionTokens) * rate;
        return BigDecimal.valueOf(cost);
    }
}
