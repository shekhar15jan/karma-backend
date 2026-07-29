package com.karmaos.execution;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final ExecutionStepRepository stepRepository;

    public ExecutionService(ExecutionRepository executionRepository, ExecutionStepRepository stepRepository) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
    }

    public List<ExecutionResponse> listByMission(UUID missionId) {
        return executionRepository.findByMissionIdOrderByCreatedAtDesc(missionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ExecutionResponse getById(UUID id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution not found: " + id));
        return toResponse(execution);
    }

    public List<ExecutionStepResponse> getSteps(UUID executionId) {
        return stepRepository.findByExecutionIdOrderByStepOrder(executionId)
                .stream()
                .map(this::toStepResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ExecutionResponse create(ExecutionRequest request) {
        Execution execution = new Execution();
        execution.setMissionId(request.getMissionId());
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
        execution.setStatus("RUNNING");
        execution.setStartedAt(LocalDateTime.now());
        execution = executionRepository.save(execution);
        return toResponse(execution);
    }

    @Transactional
    public ExecutionResponse complete(UUID id, String status, String errorMessage) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution not found: " + id));
        execution.setStatus(status != null ? status : "COMPLETED");
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
