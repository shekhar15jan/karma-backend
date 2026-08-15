package com.karmaos.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.agent.Agent;
import com.karmaos.agent.AgentRepository;
import com.karmaos.agent.AgentRuntimeService;
import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.artifact.ArtifactStorageService;
import com.karmaos.flow.Flow;
import com.karmaos.flow.FlowRepository;
import com.karmaos.junction.FlowAgent;
import com.karmaos.junction.FlowAgentRepository;
import com.karmaos.junction.MissionFlow;
import com.karmaos.junction.MissionFlowRepository;
import com.karmaos.junction.AgentSkillRepository;
import com.karmaos.media.MediaOrchestrationService;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.publication.Publication;
import com.karmaos.publishing.PublishingService;
import com.karmaos.workflow.DesignPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ExecutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExecutionOrchestrator.class);

    public static final int MAX_STEP_RETRIES = 3;
    public static final int MAX_FLOW_RESTARTS = 3;
    public static final String MODE_AUTO = "AUTO";
    public static final String MODE_REVIEW = "REVIEW";

    private final MissionRepository missionRepository;
    private final MissionFlowRepository missionFlowRepository;
    private final FlowRepository flowRepository;
    private final FlowAgentRepository flowAgentRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final AgentRepository agentRepository;
    private final ExecutionRepository executionRepository;
    private final ExecutionStepRepository stepRepository;
    private final ExecutionService executionService;
    private final AgentRuntimeService agentRuntimeService;
    private final MediaOrchestrationService mediaOrchestrationService;
    private final ArtifactRepository artifactRepository;
    private final ArtifactStorageService artifactStorageService;
    private final PublishingService publishingService;
    private final com.karmaos.sse.SseController sseController;
    private final ObjectMapper objectMapper;
    private final com.karmaos.knowledge.EmbeddingService embeddingService;

    private final Map<UUID, CompletableFuture<Void>> activeExecutions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutionOrchestrator(MissionRepository missionRepository,
                                 MissionFlowRepository missionFlowRepository,
                                 FlowRepository flowRepository,
                                 FlowAgentRepository flowAgentRepository,
                                 AgentSkillRepository agentSkillRepository,
                                 AgentRepository agentRepository,
                                 ExecutionRepository executionRepository,
                                 ExecutionStepRepository stepRepository,
                                 ExecutionService executionService,
                                 AgentRuntimeService agentRuntimeService,
                                 MediaOrchestrationService mediaOrchestrationService,
                                 ArtifactRepository artifactRepository,
                                 ArtifactStorageService artifactStorageService,
                                 PublishingService publishingService,
                                 com.karmaos.sse.SseController sseController,
                                 ObjectMapper objectMapper,
                                 com.karmaos.knowledge.EmbeddingService embeddingService) {
        this.missionRepository = missionRepository;
        this.missionFlowRepository = missionFlowRepository;
        this.flowRepository = flowRepository;
        this.flowAgentRepository = flowAgentRepository;
        this.agentSkillRepository = agentSkillRepository;
        this.agentRepository = agentRepository;
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.executionService = executionService;
        this.agentRuntimeService = agentRuntimeService;
        this.mediaOrchestrationService = mediaOrchestrationService;
        this.artifactRepository = artifactRepository;
        this.artifactStorageService = artifactStorageService;
        this.publishingService = publishingService;
        this.sseController = sseController;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public Execution startExecution(UUID missionId) {
        return startExecution(missionId, MODE_AUTO);
    }

    @Transactional
    public Execution startExecution(UUID missionId, String mode) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new RuntimeException("Mission not found: " + missionId));

        requireMissionTopic(mission);

        mission.setStatus("RUNNING");
        mission.setStartedAt(LocalDateTime.now());
        missionRepository.save(mission);

        Execution execution = new Execution();
        execution.setMissionId(missionId);
        if (mode != null && !mode.isBlank()) {
            execution.setMode(mode.toUpperCase());
        }
        execution.setStatus("PENDING");
        return executionRepository.save(execution);
    }

    public void executeAsync(UUID executionId) {
        runAsync(executionId, () -> execute(executionId));
    }

    /**
     * Starts an execution for a mission and schedules the async run only after the
     * surrounding transaction commits, so the runner can always find the execution row.
     */
    @Transactional
    public Execution startExecutionAfterCommit(UUID missionId) {
        return startExecutionAfterCommit(missionId, MODE_AUTO);
    }

    @Transactional
    public Execution startExecutionAfterCommit(UUID missionId, String mode) {
        Execution execution = startExecution(missionId, mode);
        scheduleAsync(execution.getId());
        return execution;
    }

    private void scheduleAsync(UUID executionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeAsync(executionId);
                }
            });
        } else {
            executeAsync(executionId);
        }
    }

    private void runAsync(UUID executionId, Runnable task) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Async execution {} failed", executionId, e);
                try {
                    executionService.complete(executionId, "FAILED", e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to mark execution {} FAILED", executionId, ex);
                }
                executionRepository.findById(executionId)
                        .ifPresent(ex -> updateMissionStatus(ex.getMissionId(), "FAILED"));
                emitExecutionEvent(executionId, "execution.failed",
                    "{\"executionId\":\"" + executionId + "\",\"status\":\"FAILED\"}");
            }
        }, executor);
        activeExecutions.put(executionId, future);
    }

    public void execute(UUID executionId) {
        Execution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new RuntimeException("Execution not found: " + executionId));
        executionService.start(executionId);
        emitExecutionEvent(executionId, "execution.started",
            "{\"executionId\":\"" + executionId + "\",\"status\":\"RUNNING\"}");
        runFrom(execution);
    }

    public void resumeExecution(UUID executionId) {
        Execution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Cannot resume unknown execution {}", executionId);
            return;
        }
        executionService.start(executionId);
        updateMissionStatus(execution.getMissionId(), "RUNNING");
        emitExecutionEvent(executionId, "execution.resumed",
            "{\"executionId\":\"" + executionId + "\",\"status\":\"RUNNING\"}");
        runAsync(executionId, () -> runFrom(execution));
    }

    public void regenerateStep(UUID stepId, String feedback) {
        ExecutionStep step = stepRepository.findById(stepId).orElse(null);
        if (step == null) {
            log.warn("Cannot regenerate unknown step {}", stepId);
            return;
        }
        Execution execution = executionRepository.findById(step.getExecutionId()).orElse(null);
        if (execution == null) {
            log.warn("Cannot regenerate step {} for unknown execution {}", stepId, step.getExecutionId());
            return;
        }

        stepRepository.flush();
        int rejected = countRejectedAttempts(step.getExecutionId(), step.getStepOrder());
        if (rejected >= MAX_STEP_RETRIES) {
            log.warn("Step {} rejected {} times; max retries ({}) exceeded", stepId, rejected, MAX_STEP_RETRIES);
            executionService.complete(execution.getId(), "FAILED",
                "Step rejected " + rejected + " times; max retries (" + MAX_STEP_RETRIES + ") exceeded");
            updateMissionStatus(execution.getMissionId(), "FAILED");
            return;
        }

        UUID executionId = execution.getId();
        UUID missionId = execution.getMissionId();
        int stepOrder = step.getStepOrder();
        List<MissionFlow> missionFlows = missionFlowRepository.findByIdMissionId(missionId);
        missionFlows.sort(Comparator.comparingInt(MissionFlow::getExecOrder));
        PlanHolder planHolder = buildPlan(missionFlows);
        StepPlan plan = stepOrder >= 0 && stepOrder < planHolder.steps.size()
            ? planHolder.steps.get(stepOrder) : fromStep(step);
        int retryCount = rejected;

        runAsync(executionId, () -> {
            try {
                Meta meta = readMeta(execution);
                Map<String, String> nodeOutputs = rebuildNodeOutputs(executionId);
                String context = contextFor(meta.previousOutput, plan, nodeOutputs);
                ExecutionStep rerun = runStep(executionId, plan, stepOrder, context, feedback, missionId);
                if (!"COMPLETED".equals(rerun.getStatus())) {
                    executionService.complete(executionId, "FAILED",
                        "Regeneration failed: " + rerun.getErrorMessage());
                    updateMissionStatus(missionId, "FAILED");
                    return;
                }
                if (plan.nodeId != null) {
                    rerun.setNodeId(plan.nodeId);
                }
                rerun.setRetryCount(retryCount);
                rerun.setReviewStatus("PENDING_REVIEW");
                stepRepository.save(rerun);
                meta.previousOutput = rerun.getOutputData();
                meta.resumeIndex = stepOrder + 1;
                saveMeta(execution, meta);
                executionService.pause(executionId);
                updateMissionStatus(missionId, "WAITING");
                log.info("Step {} regenerated (attempt {}), execution {} awaiting review", stepId, retryCount, executionId);
            } catch (Exception e) {
                log.error("Regeneration failed for step {}", stepId, e);
                executionService.complete(executionId, "FAILED", e.getMessage());
                updateMissionStatus(missionId, "FAILED");
            }
        });
    }

    public void restartFlow(UUID missionId, String feedback) {
        Optional<Execution> latestOpt = executionRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId);
        int restartCount = 0;
        String mode = MODE_REVIEW;
        if (latestOpt.isPresent()) {
            restartCount = readMeta(latestOpt.get()).restartCount;
            String latestMode = latestOpt.get().getMode();
            if (latestMode != null && !latestMode.isBlank()) {
                mode = latestMode;
            }
        }

        if (restartCount >= MAX_FLOW_RESTARTS) {
            int exceeded = restartCount;
            log.warn("Mission {} flow rejected {} times; max restarts ({}) exceeded", missionId, exceeded, MAX_FLOW_RESTARTS);
            updateMissionStatus(missionId, "FAILED");
            latestOpt.ifPresent(e -> executionService.complete(e.getId(), "FAILED",
                "Flow rejected " + exceeded + " times; max restarts (" + MAX_FLOW_RESTARTS + ") exceeded"));
            return;
        }

        Execution execution = new Execution();
        execution.setMissionId(missionId);
        execution.setMode(mode);
        execution.setStatus("PENDING");
        Meta meta = new Meta();
        meta.feedback = feedback;
        meta.restartCount = restartCount + 1;
        execution.setMetadata(toMetaJson(meta));
        execution = executionRepository.save(execution);

        latestOpt.ifPresent(e -> executionService.markRestarted(e.getId()));
        updateMissionStatus(missionId, "RUNNING");
        log.info("Restarting flow for mission {} (restart #{}, mode {})", missionId, restartCount + 1, mode);

        UUID newExecutionId = execution.getId();
        // The parent transaction (ReviewService.create) must commit before the new execution row
        // is visible to the async runner; otherwise it cannot find the execution and stays PENDING.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeAsync(newExecutionId);
                }
            });
        } else {
            executeAsync(newExecutionId);
        }
    }

    public void onArtifactApproved(UUID artifactId) {
        log.info("Artifact {} approved for publishing", artifactId);
        // Publish after the surrounding transaction (ArtifactService.updateReviewStatus) commits.
        // IMPORTANT: run on the executor, NOT inline in afterCommit. Running a @Transactional method
        // directly inside afterCommit joins the (already-committed) surrounding transaction and its
        // writes are never committed. A fresh thread starts its own transaction that commits normally.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.submit(() -> publishApprovedArtifact(artifactId));
                }
            });
        } else {
            executor.submit(() -> publishApprovedArtifact(artifactId));
        }
    }

    private void publishApprovedArtifact(UUID artifactId) {
        try {
            List<Publication> publications = publishingService.publishArtifact(artifactId);
            long published = publications.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
            log.info("Artifact {} published to {}/{} channels", artifactId, published, publications.size());
        } catch (Exception e) {
            log.error("Failed to publish approved artifact {}", artifactId, e);
        }
    }

    public void onArtifactRejected(UUID artifactId, String feedback) {
        Artifact artifact = artifactRepository.findById(artifactId).orElse(null);
        if (artifact == null) {
            log.warn("Cannot restart flow for unknown artifact {}", artifactId);
            return;
        }
        restartFlow(artifact.getMissionId(), feedback);
    }

    private void runFrom(Execution execution) {
        UUID executionId = execution.getId();
        UUID missionId = execution.getMissionId();
        List<MissionFlow> missionFlows = missionFlowRepository.findByIdMissionId(missionId);

        if (missionFlows.isEmpty()) {
            log.warn("No flows configured for mission {}", missionId);
            executionService.complete(executionId, "FAILED", "No flows configured for mission: " + missionId);
            updateMissionStatus(missionId, "FAILED");
            return;
        }
        missionFlows.sort(Comparator.comparingInt(MissionFlow::getExecOrder));
        PlanHolder holder = buildPlan(missionFlows);
        if (holder.steps.isEmpty()) {
            log.warn("No executable steps for mission {} (no agents assigned to its flows)", missionId);
            executionService.complete(executionId, "FAILED", "No executable steps: no agents assigned to the mission's flows");
            updateMissionStatus(missionId, "FAILED");
            return;
        }
        String mode = execution.getMode() != null ? execution.getMode() : MODE_AUTO;
        Meta meta = readMeta(execution);
        int startIndex = meta.resumeIndex;
        String previousOutput = meta.previousOutput != null ? meta.previousOutput : meta.feedback;
        int totalSteps = holder.steps.size();

        Map<String, String> nodeOutputs = rebuildNodeOutputs(executionId);

        try {
            for (int i = startIndex; i < holder.steps.size(); i++) {
                StepPlan sp = holder.steps.get(i);
                String context = contextFor(previousOutput, sp, nodeOutputs);
                ExecutionStep step = runStep(executionId, sp, i, context, null, missionId);

                if (!"COMPLETED".equals(step.getStatus())) {
                    executionService.complete(executionId, "FAILED",
                        "Step failed: flow=" + sp.flowId + " agent=" + sp.agentId + " error=" + step.getErrorMessage());
                    updateMissionStatus(missionId, "FAILED");
                    return;
                }

                if (sp.nodeId != null) {
                    step.setNodeId(sp.nodeId);
                    stepRepository.save(step);
                    nodeOutputs.put(sp.nodeId, step.getOutputData());
                }
                previousOutput = step.getOutputData();
                
                // AUTOMATED QUALITY LOOP
                if (previousOutput != null && previousOutput.contains("\"revise_and_resubmit\"")) {
                    log.info("Automated Critic requested revision. Restarting from previous steps with feedback.");
                    emitExecutionEvent(executionId, "execution.quality_loop", "{\"message\":\"Quality scores below threshold, auto-reprompting\"}");
                    
                    // Simple heuristic: re-run the previous agent (index - 1) with this output as feedback
                    if (i > 0) {
                        i = i - 2; // Subtract 2 because loop will do i++ next iteration, effectively jumping to i-1
                        continue;
                    }
                }

                updateMissionProgress(missionId,
                        totalSteps > 0 ? (int) Math.round(((i + 1) * 100.0) / totalSteps) : 100);
                emitExecutionEvent(executionId, "step.completed",
                    "{\"executionId\":\"" + executionId + "\",\"stepOrder\":" + i
                        + ",\"status\":\"COMPLETED\",\"stepId\":\"" + step.getId() + "\"}");

                if (MODE_REVIEW.equals(mode)) {
                    step.setReviewStatus("PENDING_REVIEW");
                    stepRepository.save(step);
                    meta.resumeIndex = i + 1;
                    meta.previousOutput = previousOutput;
                    saveMeta(execution, meta);
                    executionService.pause(executionId);
                    updateMissionStatus(missionId, "WAITING");
                    emitExecutionEvent(executionId, "execution.waiting",
                        "{\"executionId\":\"" + executionId + "\",\"status\":\"WAITING\",\"stepOrder\":" + i + "}");
                    log.info("Execution {} paused at step index {} awaiting review", executionId, i);
                    return;
                }
            }

            createArtifactsFromOutput(execution, missionFlows, holder.mediaFlows);
            executionService.complete(executionId, "COMPLETED", null);
            updateMissionStatus(missionId, "COMPLETED");
            emitExecutionEvent(executionId, "execution.completed",
                "{\"executionId\":\"" + executionId + "\",\"status\":\"COMPLETED\"}");
        } catch (Exception e) {
            log.error("Execution {} failed with exception", executionId, e);
            executionService.complete(executionId, "FAILED", e.getMessage());
            updateMissionStatus(missionId, "FAILED");
            emitExecutionEvent(executionId, "execution.failed",
                "{\"executionId\":\"" + executionId + "\",\"status\":\"FAILED\"}");
        }
    }

    private ExecutionStep runStep(UUID executionId, StepPlan sp, int stepOrder, String previousOutput,
                                  String reviewerFeedback, UUID missionId) {
        return switch (sp.kind) {
            case "PUBLISH" -> mediaOrchestrationService.executePublishStep(
                executionId, sp.flowId, sp.agentId, stepOrder, previousOutput, missionId);
            case "IMAGE" -> mediaOrchestrationService.executeImageStep(
                executionId, sp.flowId, sp.agentId, stepOrder, previousOutput, missionId, reviewerFeedback);
            case "VOICE" -> mediaOrchestrationService.executeVoiceStep(
                executionId, sp.flowId, sp.agentId, stepOrder, previousOutput, missionId, reviewerFeedback);
            case "MUSIC" -> mediaOrchestrationService.executeMusicStep(
                executionId, sp.flowId, sp.agentId, stepOrder, previousOutput, missionId, reviewerFeedback);
            case "VIDEO" -> mediaOrchestrationService.executeVideoStep(
                executionId, sp.flowId, sp.agentId, stepOrder, previousOutput, missionId, reviewerFeedback);
            default -> agentRuntimeService.executeAgentStep(
                executionId, sp.flowId, sp.agentId, stepOrder, previousOutput, missionId, reviewerFeedback);
        };
    }

    private PlanHolder buildPlan(List<MissionFlow> missionFlows) {
        List<StepPlan> steps = new ArrayList<>();
        Set<UUID> mediaFlows = new HashSet<>();

        for (MissionFlow mf : missionFlows) {
            UUID flowId = mf.getId().getFlowId();
            Flow flow = flowRepository.findByIdAndStatus(flowId, "ACTIVE").orElse(null);
            if (flow == null) {
                log.warn("Flow {} not found or inactive; skipping in plan", flowId);
                continue;
            }

            DesignPlanHolder design = parseDesign(flow);
            if (design != null) {
                buildDesignSteps(flowId, flow, design, steps, mediaFlows);
                continue;
            }

            List<FlowAgent> flowAgents = flowAgentRepository.findByIdFlowIdOrderByStepOrderAsc(flowId);
            if (flowAgents.isEmpty()) {
                log.warn("No agents assigned to flow {}", flowId);
                continue;
            }

            String category = flow.getCategory() != null ? flow.getCategory() : "";

            for (FlowAgent fa : flowAgents) {
                Agent agent = fa.getId() != null ? agentRepository.findById(fa.getId().getAgentId()).orElse(null) : null;
                if (agent != null && agentSkillRepository.findByIdAgentId(agent.getId()).isEmpty()) {
                    throw new RuntimeException("Agent '" + agent.getName() + "' has no skills assigned. "
                        + "Skills are a mandatory behavioral contract; assign a skill before executing.");
                }
                String kind = resolveStepKind(category, agent);
                if ("IMAGE".equals(kind) && "VIDEO".equalsIgnoreCase(category)) {
                    log.info("Dropping IMAGE step from VIDEO flow plan as per B5");
                    continue;
                }
                if (isMediaKind(kind)) {
                    mediaFlows.add(flowId);
                }
                steps.add(new StepPlan(flowId, fa.getId().getAgentId(), kind));
            }
        }
        return new PlanHolder(steps, mediaFlows);
    }

    private DesignPlanHolder parseDesign(Flow flow) {
        String parameters = flow.getParameters();
        if (parameters == null || parameters.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(parameters);
            if (root == null || !root.hasNonNull(DesignPlan.KEY_DESIGN)) {
                return null;
            }
            JsonNode design = root.get(DesignPlan.KEY_DESIGN);
            List<Map<String, Object>> nodes = objectMapper.convertValue(design.get("nodes"),
                new TypeReference<List<Map<String, Object>>>() { });
            List<Map<String, String>> edges = objectMapper.convertValue(design.get("edges"),
                new TypeReference<List<Map<String, String>>>() { });
            return new DesignPlanHolder(DesignPlan.resolve(nodes, edges));
        } catch (Exception e) {
            log.warn("Invalid design parameters for flow {}: {}", flow.getId(), e.getMessage());
            return null;
        }
    }

    private void buildDesignSteps(UUID flowId, Flow flow, DesignPlanHolder design,
                                  List<StepPlan> steps, Set<UUID> mediaFlows) {
        String category = flow.getCategory() != null ? flow.getCategory() : "";
        for (DesignPlan.Node node : design.nodes) {
            Agent agent = node.agentId != null ? agentRepository.findById(node.agentId).orElse(null) : null;
            if (agent != null && agentSkillRepository.findByIdAgentId(agent.getId()).isEmpty()) {
                throw new RuntimeException("Agent '" + agent.getName() + "' has no skills assigned. "
                    + "Skills are a mandatory behavioral contract; assign a skill before executing.");
            }
            String kind = resolveDesignKind(node.stepKind, category, agent);
            if ("IMAGE".equals(kind) && "VIDEO".equalsIgnoreCase(category)) {
                log.info("Dropping IMAGE step from VIDEO flow plan as per B5");
                continue;
            }
            if (isMediaKind(kind)) {
                mediaFlows.add(flowId);
            }
            steps.add(new StepPlan(flowId, node.agentId, kind, node.nodeId, node.upstreams));
        }
    }

    private String resolveDesignKind(String stepKind, String flowCategory, Agent agent) {
        if (stepKind != null && !stepKind.isBlank()) {
            String kind = stepKind.trim().toUpperCase();
            switch (kind) {
                case "VIDEO":
                case "PUBLISH":
                case "IMAGE":
                case "VOICE":
                case "MUSIC":
                    return kind;
                default:
                    // "AGENT" or unknown kinds fall through to inference
                    break;
            }
        }
        return resolveStepKind(flowCategory, agent);
    }

    private String resolveStepKind(String flowCategory, Agent agent) {
        if (agent == null) {
            return "LLM";
        }
        String agentName = agent.getName() != null ? agent.getName().toLowerCase() : "";
        String agentCategory = agent.getCategory() != null ? agent.getCategory().toUpperCase() : "";
        if ("PUBLISHING".equals(agentCategory) || agentName.contains("publish")) {
            return "PUBLISH";
        }
        if ("MEDIA".equals(agentCategory)) {
            if (agentName.contains("image") || agentName.contains("thumbnail")) {
                return "IMAGE";
            }
            if (agentName.contains("voice") || agentName.contains("audio")) {
                return "VOICE";
            }
            if (agentName.contains("music")) {
                return "MUSIC";
            }
            if (agentName.contains("video")) {
                return "VIDEO";
            }
            return "LLM";
        }
        return "LLM";
    }

    private boolean isMediaKind(String kind) {
        return "IMAGE".equals(kind) || "VOICE".equals(kind) || "MUSIC".equals(kind)
                || "VIDEO".equals(kind) || "PUBLISH".equals(kind);
    }

    private StepPlan fromStep(ExecutionStep step) {
        return new StepPlan(step.getFlowId(), step.getAgentId(),
            switch (step.getStepType() != null ? step.getStepType() : "") {
                case "PUBLISH" -> "PUBLISH";
                case "IMAGE_GENERATION" -> "IMAGE";
                case "TTS" -> "VOICE";
                case "MUSIC_GENERATION" -> "MUSIC";
                case "VIDEO_GENERATION" -> "VIDEO";
                default -> "LLM";
            }, step.getNodeId(), Collections.emptyList());
    }

    private int countRejectedAttempts(UUID executionId, int stepOrder) {
        return (int) stepRepository.findByExecutionIdOrderByStepOrder(executionId).stream()
            .filter(s -> s.getStepOrder() == stepOrder && "REJECTED".equals(s.getReviewStatus()))
            .count();
    }

    private Map<String, String> rebuildNodeOutputs(UUID executionId) {
        Map<String, String> outputs = new HashMap<>();
        for (ExecutionStep step : stepRepository.findByExecutionIdOrderByStepOrder(executionId)) {
            if (step.getNodeId() != null && "COMPLETED".equals(step.getStatus())
                    && step.getOutputData() != null && !step.getOutputData().isBlank()) {
                outputs.put(step.getNodeId(), step.getOutputData());
            }
        }
        return outputs;
    }

    private String contextFor(String fallback, StepPlan sp, Map<String, String> nodeOutputs) {
        if (sp.nodeId == null || sp.upstreams.isEmpty()) {
            return fallback;
        }
        StringBuilder context = new StringBuilder();
        for (String upstream : sp.upstreams) {
            String output = nodeOutputs.get(upstream);
            if (output == null || output.isBlank()) {
                continue;
            }
            if (context.length() > 0) {
                context.append("\n\n");
            }
            context.append(output);
        }
        return context.length() > 0 ? context.toString() : fallback;
    }

    private void createArtifactsFromOutput(Execution execution, List<MissionFlow> missionFlows,
                                           Set<UUID> mediaFlows) {
        UUID missionId = execution.getMissionId();
        Map<UUID, String> flowOutputs = lastOutputPerFlow(execution.getId());

        for (MissionFlow mf : missionFlows) {
            UUID flowId = mf.getId().getFlowId();
            if (mediaFlows.contains(flowId)) continue;

            Flow flow = flowRepository.findByIdAndStatus(flowId, "ACTIVE").orElse(null);
            if (flow == null) continue;

            List<FlowAgent> agents = flowAgentRepository.findByIdFlowIdOrderByStepOrderAsc(flowId);
            if (agents.isEmpty()) continue;

            String flowOutput = flowOutputs.get(flowId);
            if (flowOutput == null || flowOutput.isBlank()) continue;

            String artifactType = switch (flow.getCategory() != null ? flow.getCategory() : "") {
                case "RESEARCH" -> "RESEARCH";
                case "PLANNING" -> "PLAN";
                case "CONTENT" -> "SCRIPT";
                case "MEDIA" -> "MEDIA_ASSET";
                case "REVIEW" -> "REVIEW_REPORT";
                case "PUBLISHING" -> "PUBLICATION";
                case "DESIGNER" -> "WORKFLOW_OUTPUT";
                default -> "GENERAL";
            };

            Artifact artifact = new Artifact();
            artifact.setMissionId(missionId);
            artifact.setName(flow.getName() + " Output");
            artifact.setArtifactType(artifactType);
            artifact.setStatus("COMPLETED");
            artifact.setContentText(flowOutput);
            artifact.setContentType("text/markdown");
            artifact.setReviewStatus("PENDING");
            artifact.setPublicationStatus("UNPUBLISHED");
            artifactStorageService.persist(artifact, missionId);
            artifactRepository.save(artifact);
            embeddingService.indexArtifact(artifact.getId(), artifact.getContentText());
        }
    }

    private Map<UUID, String> lastOutputPerFlow(UUID executionId) {
        Map<UUID, String> result = new HashMap<>();
        List<ExecutionStep> steps = stepRepository.findByExecutionIdOrderByStepOrder(executionId);
        for (ExecutionStep step : steps) {
            if (!"COMPLETED".equals(step.getStatus()) || step.getFlowId() == null) continue;
            String output = step.getOutputData();
            if (output == null || output.isBlank()) continue;
            if ("IMAGE".equals(step.getStepType()) || "TTS".equals(step.getStepType())
                    || "MUSIC_GENERATION".equals(step.getStepType())
                    || "VIDEO_GENERATION".equals(step.getStepType()) || "PUBLISH".equals(step.getStepType())) {
                continue;
            }
            result.put(step.getFlowId(), output);
        }
        return result;
    }

    public void cancelExecution(UUID executionId) {
        CompletableFuture<Void> future = activeExecutions.get(executionId);
        if (future != null) {
            future.cancel(true);
            activeExecutions.remove(executionId);
        }
        executionService.complete(executionId, "CANCELLED", "Execution cancelled by user");
        emitExecutionEvent(executionId, "execution.cancelled",
            "{\"executionId\":\"" + executionId + "\",\"status\":\"CANCELLED\"}");
        Execution execution = executionRepository.findById(executionId).orElse(null);
        if (execution != null) {
            updateMissionStatus(execution.getMissionId(), "CANCELLED");
        }
    }

    public String getExecutionStatus(UUID executionId) {
        return executionRepository.findById(executionId)
            .map(Execution::getStatus)
            .orElse("NOT_FOUND");
    }

    public boolean isActive(UUID executionId) {
        CompletableFuture<Void> future = activeExecutions.get(executionId);
        return future != null && !future.isDone();
    }

    private void requireMissionTopic(Mission mission) {
        String name = mission.getName() != null ? mission.getName().trim() : "";
        String description = mission.getDescription() != null ? mission.getDescription().trim() : "";
        String lowerName = name.toLowerCase();
        boolean genericName = name.isBlank()
            || "untitled".equals(lowerName)
            || "video".equals(lowerName)
            || "video workflow".equals(lowerName)
            || "workflow".equals(lowerName)
            || "mission".equals(lowerName)
            || "blog".equals(lowerName)
            || "thumbnail".equals(lowerName);
        if (genericName && description.isBlank()) {
            throw new RuntimeException(
                "No mission topic provided. Set a meaningful mission name or description (the topic/brief "
                    + "for this video) before triggering execution.");
        }
    }

    private void updateMissionStatus(UUID missionId, String status) {
        missionRepository.findById(missionId).ifPresent(mission -> {
            mission.setStatus(status);
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                mission.setCompletedAt(LocalDateTime.now());
            }
            if ("COMPLETED".equals(status)) {
                mission.setProgress(100);
            }
            missionRepository.save(mission);
            emitMissionEvent(missionId, status);
        });
    }

    private void emitExecutionEvent(UUID executionId, String eventType, String data) {
        try {
            sseController.sendEvent(eventType, data);
        } catch (Exception e) {
            log.debug("SSE emit failed for {} {}: {}", eventType, executionId, e.getMessage());
        }
    }

    private void emitMissionEvent(UUID missionId, String status) {
        emitExecutionEvent(missionId, "mission." + status.toLowerCase(),
            "{\"missionId\":\"" + missionId + "\",\"status\":\"" + status + "\"}");
    }

    private void updateMissionProgress(UUID missionId, int progress) {
        missionRepository.findById(missionId).ifPresent(mission -> {
            mission.setProgress(progress);
            missionRepository.save(mission);
        });
    }

    private Meta readMeta(Execution execution) {
        if (execution.getMetadata() == null || execution.getMetadata().isBlank()) {
            return new Meta();
        }
        try {
            return objectMapper.readValue(execution.getMetadata(), Meta.class);
        } catch (Exception e) {
            log.warn("Failed to parse execution metadata for {}: {}", execution.getId(), e.getMessage());
            return new Meta();
        }
    }

    private String toMetaJson(Meta meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            log.warn("Failed to serialize execution metadata: {}", e.getMessage());
            return "{}";
        }
    }

    private void saveMeta(Execution execution, Meta meta) {
        execution.setMetadata(toMetaJson(meta));
        executionRepository.save(execution);
    }

    private static class Meta {
        public int resumeIndex = 0;
        public String previousOutput;
        public String feedback;
        public int restartCount = 0;
    }

    private static class StepPlan {
        final UUID flowId;
        final UUID agentId;
        final String kind;
        final String nodeId;
        final List<String> upstreams;

        StepPlan(UUID flowId, UUID agentId, String kind) {
            this(flowId, agentId, kind, null, Collections.emptyList());
        }

        StepPlan(UUID flowId, UUID agentId, String kind, String nodeId, List<String> upstreams) {
            this.flowId = flowId;
            this.agentId = agentId;
            this.kind = kind;
            this.nodeId = nodeId;
            this.upstreams = upstreams == null ? Collections.emptyList() : upstreams;
        }
    }

    private static class DesignPlanHolder {
        final List<DesignPlan.Node> nodes;

        DesignPlanHolder(List<DesignPlan.Node> nodes) {
            this.nodes = nodes;
        }
    }

    private static class PlanHolder {
        final List<StepPlan> steps;
        final Set<UUID> mediaFlows;

        PlanHolder(List<StepPlan> steps, Set<UUID> mediaFlows) {
            this.steps = steps;
            this.mediaFlows = mediaFlows;
        }
    }
}
