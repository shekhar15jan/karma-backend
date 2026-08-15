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
import com.karmaos.settings.RuntimeSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.util.*;
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
                               LlmCacheRepository llmCacheRepository) {
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

        try {
            Flow flow = flowRepository.findByIdAndStatus(flowId, "ACTIVE").orElse(null);
            Agent agent = agentRepository.findById(agentId).orElse(null);
            if (agent == null) {
                throw new RuntimeException("Agent not found: " + agentId);
            }

            String distilledContext = StepContextDistiller.distill(contextData);
            String systemPrompt = buildSystemPrompt(agent, flow, missionId);
            String userMessage = buildUserMessage(agent, distilledContext, missionId, reviewerFeedback);

            Provider primaryProvider = resolveProvider(agent);
            step.setProviderId(primaryProvider != null ? primaryProvider.getId() : null);

            LLMResponse response = executeWithRetryAndFallback(agent, primaryProvider, step,
                    systemPrompt, userMessage, missionId, resolveMaxTokens(flow, agent, missionId));
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
     * Runs the LLM call with up to MAX_ATTEMPTS attempts, each time trying the
     * primary provider first and the agent's fallback provider second. The prompt
     * is augmented with a corrective instruction after a failed/empty attempt, so
     * the model gets a chance to produce a valid, non-stub response.
     */
    private LLMResponse executeWithRetryAndFallback(Agent agent, Provider primaryProvider, ExecutionStep step,
                                                    String systemPrompt, String userMessage, UUID missionId, int maxTokens) {
        final int maxAttempts = 3;
        LLMResponse lastResponse = null;
        String correctiveHint = "";

        Provider[] chain;
        Provider fallback = agent != null && agent.getFallbackProviderId() != null
            ? providerRepository.findById(agent.getFallbackProviderId()).orElse(null)
            : null;
        if (primaryProvider != null && fallback != null && !primaryProvider.getId().equals(fallback.getId())) {
            chain = new Provider[] { primaryProvider, fallback };
        } else if (primaryProvider != null) {
            chain = new Provider[] { primaryProvider };
        } else {
            chain = new Provider[0];
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            for (Provider provider : chain) {
                LLMProvider llmProvider = resolveLLMProvider(provider);
                if (llmProvider == null || "mock".equals(llmProvider.getName())) {
                    continue;
                }
                String modelUsed = resolveModel(agent, provider, llmProvider);
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
                    if (lastResponse.isSuccess() && isOutputValidFor(agent, lastResponse.getContent())) {
                        LlmCache newCache = new LlmCache();
                        newCache.setHashKey(hashKey);
                        newCache.setModel(modelUsed);
                        newCache.setResponseContent(lastResponse.getContent());
                        llmCacheRepository.save(newCache);
                    }
                }

                if (lastResponse.isSuccess() && isOutputValidFor(agent, lastResponse.getContent())) {
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

                if (!lastResponse.isSuccess() && lastResponse.getErrorMessage() != null) {
                    String err = lastResponse.getErrorMessage().toLowerCase();
                    if (err.contains("429") || err.contains("rate limit") || err.contains("too many requests")) {
                        long sleepTime = (long) (Math.pow(2, attempt) * 1000) + (long) (Math.random() * 1000);
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
                    correctiveHint = "\n\nCORRECTIVE INSTRUCTION: Your previous response was rejected because it did NOT "
                        + "conform to the mandated JSON schema defined in your assigned skill (\"Video Script Generator\"). "
                        + "Respond with a SINGLE raw JSON object only (no markdown "
                        + "fences, no ```json, no preamble, no commentary) that has EXACTLY these top-level fields: "
                        + "{\"title\": \"...\", \"script_body\": [ {\"scene_number\": 1, \"speaker_text\": \"...\", "
                        + "\"visual_description\": \"...\", \"visual_spec\": { \"theme\": \"codeatcloud\", \"layout\": \"presentation\", "
                        + "\"title\": \"...\", \"content_blocks\": [ "
                        + "{\"heading\": \"...\", \"text\": \"...\"} ] } } ] }. "
                        + "The \"script_body\" MUST be a non-empty JSON array of scene objects; each scene MUST have "
                        + "\"scene_number\", \"speaker_text\" and a \"visual_spec\" object. Each \"visual_spec\" MUST have either a "
                        + "\"components\" array OR a \"content_blocks\" array for presentation layouts. "
                        + "Follow the layouts, themes and component vocabulary from your assigned skill. "
                        + "Do NOT output a content strategy, outline, plan, or any other JSON shape. Output ONLY the "
                        + "script_body JSON object.";
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

    private String buildSystemPrompt(Agent agent, Flow flow, UUID missionId) {
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
              .append("-").append(target.wordsPerScene + 25)
              .append(" words (spoken at ~2.3 words per second, so each scene is roughly ")
              .append(Math.round(target.wordsPerScene / 2.3)).append("-").append(Math.round((target.wordsPerScene + 25) / 2.3))
              .append(" seconds). ")
              .append("Make the script complete and non-branded. ")
              .append("STRICT CONTENT SAFETY RULES: The content must be family-friendly, age-appropriate and suitable for all audiences. ")
              .append("ABSOLUTELY FORBIDDEN: any violence, fighting, war, weapons, guns, swords, blood, gore, injury, death, killing, ")
              .append("fear, horror, nudity, adult/sexual content, drugs, alcohol, smoking, or anything inappropriate. ")
              .append("Follow the behavioral contract described in the skill above for style, tone and diagram rules.\n")
              .append("REPEAT: Output ONLY the raw JSON object described by your skill. Do NOT include markdown headings, bullet lists, ")
              .append("explanatory prose, chain-of-thought reasoning, or any text outside the JSON.");
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
    private boolean isOutputValidFor(Agent agent, String content) {
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
                int minScenes = Math.max(1, target.minScenes());
                if (body.size() < minScenes) {
                    log.info("Script output rejected: {} scenes found, minimum required is {}",
                            body.size(), minScenes);
                    return false;
                }
                for (JsonNode scene : body) {
                    JsonNode spec = scene != null
                        ? (scene.hasNonNull("visual_spec") ? scene.get("visual_spec") : scene.get("diagram_spec"))
                        : null;
                    if (spec == null || !spec.isObject()) {
                        return false;
                    }
                    JsonNode shapes = spec.has("components") ? spec.get("components") : spec.get("shapes");
                    JsonNode blocks = spec.get("content_blocks");
                    
                    if ((shapes == null || !shapes.isArray() || shapes.size() == 0) &&
                        (blocks == null || !blocks.isArray() || blocks.size() == 0)) {
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
        int maxScenes = runtimeSettings.getInt(RuntimeSettings.KEY_VIDEO_MAX_SCENES, 36);
        int wordsPerScene = runtimeSettings.getInt(RuntimeSettings.KEY_VIDEO_WORDS_PER_SCENE, 70);

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
                }
            } catch (Exception ignored) {
            }
        }

        return new StoryTarget(targetDuration, minScenes, maxScenes, wordsPerScene);
    }

    private record StoryTarget(int targetDurationSeconds, int minScenes, int maxScenes, int wordsPerScene) {
        StoryTarget {
            if (targetDurationSeconds <= 0) targetDurationSeconds = 900;
            if (minScenes <= 0) minScenes = 30;
            if (maxScenes < minScenes) maxScenes = minScenes;
            if (wordsPerScene <= 0) wordsPerScene = 70;
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

    private String resolveModel(Agent agent, Provider provider, LLMProvider llmProvider) {
        String configured = configuredModel(agent);
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
