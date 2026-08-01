package com.karmaos.execution;

import com.karmaos.access.OwnershipService;
import com.karmaos.agent.Agent;
import com.karmaos.agent.AgentRepository;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.flow.Flow;
import com.karmaos.flow.FlowRepository;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final ExecutionStepRepository stepRepository;
    private final MissionRepository missionRepository;
    private final FlowRepository flowRepository;
    private final AgentRepository agentRepository;
    private final OwnershipService ownershipService;

    public ExecutionService(ExecutionRepository executionRepository, ExecutionStepRepository stepRepository,
                            MissionRepository missionRepository, FlowRepository flowRepository,
                            AgentRepository agentRepository, OwnershipService ownershipService) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.missionRepository = missionRepository;
        this.flowRepository = flowRepository;
        this.agentRepository = agentRepository;
        this.ownershipService = ownershipService;
    }

    public List<ExecutionResponse> listByMission(UUID missionId, UserPrincipal user) {
        ownershipService.getOwnedMission(user.getId(), missionId);
        return executionRepository.findByMissionIdOrderByCreatedAtDesc(missionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ExecutionResponse getById(UUID id, UserPrincipal user) {
        return toResponse(ownershipService.getOwnedExecution(user.getId(), id));
    }

    public List<ExecutionStepResponse> getSteps(UUID executionId, UserPrincipal user) {
        ownershipService.getOwnedExecution(user.getId(), executionId);
        return stepRepository.findByExecutionIdOrderByStepOrder(executionId)
                .stream()
                .map(this::toStepResponse)
                .collect(Collectors.toList());
    }

    public List<ExecutionStepResponse> listStepsByAgent(UUID agentId, UserPrincipal user) {
        Set<UUID> ownedExecutionIds = executionRepository
                .findByMissionIdInOrderByCreatedAtDesc(ownershipService.getOwnedMissionIds(user.getId()))
                .stream()
                .map(Execution::getId)
                .collect(Collectors.toSet());
        return stepRepository.findByAgentIdOrderByCreatedAtDesc(agentId).stream()
                .filter(s -> ownedExecutionIds.contains(s.getExecutionId()))
                .map(this::toStepResponse)
                .collect(Collectors.toList());
    }

    public List<ExecutionResponse> listOwned(UserPrincipal user) {
        return executionRepository.findByMissionIdInOrderByCreatedAtDesc(
                        ownershipService.getOwnedMissionIds(user.getId()))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PendingStepReviewResponse> listPendingStepReviews(UUID userId) {
        List<UUID> executionIds = executionRepository.findByMissionIdInOrderByCreatedAtDesc(
                        ownershipService.getOwnedMissionIds(userId))
                .stream()
                .map(Execution::getId)
                .collect(Collectors.toList());
        if (executionIds.isEmpty()) {
            return List.of();
        }
        return stepRepository.findByReviewStatusInAndExecutionIdIn(
                        List.of("PENDING_REVIEW"), executionIds)
                .stream()
                .map(this::toPendingStepReviewResponse)
                .collect(Collectors.toList());
    }

    private PendingStepReviewResponse toPendingStepReviewResponse(ExecutionStep s) {
        Mission mission = null;
        if (s.getExecutionId() != null) {
            Optional<Execution> execution = executionRepository.findById(s.getExecutionId());
            if (execution.isPresent() && execution.get().getMissionId() != null) {
                mission = missionRepository.findById(execution.get().getMissionId()).orElse(null);
            }
        }
        Flow flow = s.getFlowId() != null ? flowRepository.findByIdAndStatus(s.getFlowId(), "ACTIVE").orElse(null) : null;
        Agent agent = s.getAgentId() != null ? agentRepository.findById(s.getAgentId()).orElse(null) : null;

        return PendingStepReviewResponse.builder()
                .stepId(s.getId())
                .executionId(s.getExecutionId())
                .missionId(mission != null ? mission.getId() : null)
                .missionName(mission != null ? mission.getName() : null)
                .flowId(s.getFlowId())
                .flowName(flow != null ? flow.getName() : null)
                .agentId(s.getAgentId())
                .agentName(agent != null ? agent.getName() : null)
                .stepOrder(s.getStepOrder())
                .stepType(s.getStepType())
                .status(s.getStatus())
                .reviewStatus(s.getReviewStatus())
                .inputData(s.getInputData())
                .outputData(s.getOutputData())
                .retryCount(s.getRetryCount())
                .createdAt(s.getCreatedAt())
                .build();
    }

    @Transactional
    public ExecutionResponse create(ExecutionRequest request) {
        Execution execution = new Execution();
        execution.setMissionId(request.getMissionId());
        if (request.getMode() != null && !request.getMode().isBlank()) {
            execution.setMode(request.getMode().toUpperCase());
        }
        execution.setProviderId(request.getProviderId());
        execution.setModelUsed(request.getModelUsed());
        execution.setStatus("PENDING");
        execution = executionRepository.save(execution);
        return toResponse(execution);
    }

    @Transactional
    public ExecutionResponse start(UUID id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution not found: " + id));
        String current = execution.getStatus() != null ? execution.getStatus().toUpperCase() : "";
        if ("COMPLETED".equals(current) || "CANCELLED".equals(current) || "RESTARTED".equals(current)) {
            throw ErrorCode.CONFLICT.exception("Execution is already in a terminal state: " + execution.getStatus());
        }
        execution.setStatus("RUNNING");
        execution.setStartedAt(LocalDateTime.now());
        execution = executionRepository.save(execution);
        return toResponse(execution);
    }

    @Transactional
    public ExecutionResponse pause(UUID id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution not found: " + id));
        execution.setStatus("WAITING");
        execution = executionRepository.save(execution);
        return toResponse(execution);
    }

    @Transactional
    public ExecutionResponse markRestarted(UUID id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution not found: " + id));
        execution.setStatus("RESTARTED");
        execution.setCompletedAt(LocalDateTime.now());
        if (execution.getStartedAt() != null) {
            execution.setDurationMs(java.time.Duration.between(execution.getStartedAt(), execution.getCompletedAt()).toMillis());
        }
        execution = executionRepository.save(execution);
        return toResponse(execution);
    }

    @Transactional
    public ExecutionResponse complete(UUID id, String status, String errorMessage) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution not found: " + id));
        String target = status != null && !status.isBlank() ? status.toUpperCase() : "COMPLETED";
        if (!Set.of("COMPLETED", "FAILED", "CANCELLED").contains(target)) {
            throw ErrorCode.BAD_REQUEST.exception("Invalid terminal status: " + status);
        }
        execution.setStatus(target);
        execution.setCompletedAt(LocalDateTime.now());
        execution.setErrorMessage(errorMessage);
        if (execution.getStartedAt() != null) {
            execution.setDurationMs(java.time.Duration.between(execution.getStartedAt(), execution.getCompletedAt()).toMillis());
        }
        execution = executionRepository.save(execution);
        return toResponse(execution);
    }

    @Transactional
    public ExecutionStepResponse addStep(UUID executionId, ExecutionStep step) {
        step.setExecutionId(executionId);
        step = stepRepository.save(step);
        return toStepResponse(step);
    }

    @Transactional
    public ExecutionStep saveStep(UUID executionId, ExecutionStep step) {
        step.setExecutionId(executionId);
        return stepRepository.save(step);
    }

    @Transactional
    public ExecutionStepResponse updateStepStatus(UUID stepId, String status, String outputData, String errorMessage) {
        ExecutionStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution step not found: " + stepId));
        step.setStatus(status);
        step.setOutputData(outputData != null ? outputData : step.getOutputData());
        step.setErrorMessage(errorMessage);
        if ("RUNNING".equals(status) && step.getStartedAt() == null) {
            step.setStartedAt(LocalDateTime.now());
        }
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            step.setCompletedAt(LocalDateTime.now());
        }
        step = stepRepository.save(step);
        return toStepResponse(step);
    }

    private ExecutionResponse toResponse(Execution e) {
        return ExecutionResponse.builder()
                .id(e.getId())
                .missionId(e.getMissionId())
                .mode(e.getMode())
                .status(e.getStatus())
                .startedAt(e.getStartedAt())
                .completedAt(e.getCompletedAt())
                .durationMs(e.getDurationMs())
                .providerId(e.getProviderId())
                .modelUsed(e.getModelUsed())
                .totalCost(e.getTotalCost())
                .errorMessage(e.getErrorMessage())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private ExecutionStepResponse toStepResponse(ExecutionStep s) {
        return ExecutionStepResponse.builder()
                .id(s.getId())
                .executionId(s.getExecutionId())
                .flowId(s.getFlowId())
                .agentId(s.getAgentId())
                .stepOrder(s.getStepOrder())
                .stepType(s.getStepType())
                .status(s.getStatus())
                .reviewStatus(s.getReviewStatus())
                .inputData(s.getInputData())
                .outputData(s.getOutputData())
                .promptUsed(s.getPromptUsed())
                .promptVersion(s.getPromptVersion())
                .providerId(s.getProviderId())
                .modelUsed(s.getModelUsed())
                .promptTokens(s.getPromptTokens())
                .completionTokens(s.getCompletionTokens())
                .totalTokens(s.getTotalTokens())
                .cost(s.getCost())
                .latencyMs(s.getLatencyMs())
                .errorMessage(s.getErrorMessage())
                .retryCount(s.getRetryCount())
                .startedAt(s.getStartedAt())
                .completedAt(s.getCompletedAt())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
