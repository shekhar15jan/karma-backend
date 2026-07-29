package com.karmaos.execution;

import com.karmaos.agent.AgentRuntimeService;
import com.karmaos.flow.FlowRepository;
import com.karmaos.junction.FlowAgent;
import com.karmaos.junction.FlowAgentRepository;
import com.karmaos.junction.MissionFlow;
import com.karmaos.junction.MissionFlowRepository;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ExecutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExecutionOrchestrator.class);

    private final MissionRepository missionRepository;
    private final MissionFlowRepository missionFlowRepository;
    private final FlowRepository flowRepository;
    private final FlowAgentRepository flowAgentRepository;
    private final ExecutionRepository executionRepository;
    private final ExecutionStepRepository stepRepository;
    private final ExecutionService executionService;
    private final AgentRuntimeService agentRuntimeService;

    private final Map<UUID, CompletableFuture<Void>> activeExecutions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutionOrchestrator(MissionRepository missionRepository,
                                 MissionFlowRepository missionFlowRepository,
                                 FlowRepository flowRepository,
                                 FlowAgentRepository flowAgentRepository,
                                 ExecutionRepository executionRepository,
                                 ExecutionStepRepository stepRepository,
                                 ExecutionService executionService,
                                 AgentRuntimeService agentRuntimeService) {
        this.missionRepository = missionRepository;
        this.missionFlowRepository = missionFlowRepository;
        this.flowRepository = flowRepository;
        this.flowAgentRepository = flowAgentRepository;
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.executionService = executionService;
        this.agentRuntimeService = agentRuntimeService;
    }

    @Transactional
    public Execution startExecution(UUID missionId) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new RuntimeException("Mission not found: " + missionId));

        mission.setStatus("RUNNING");
        mission.setStartedAt(LocalDateTime.now());
        missionRepository.save(mission);

        Execution execution = new Execution();
        execution.setMissionId(missionId);
        execution.setStatus("PENDING");
        execution = executionRepository.save(execution);

        return execution;
    }

    public void executeAsync(UUID executionId) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                execute(executionId);
            } catch (Exception e) {
                log.error("Execution {} failed", executionId, e);
                executionService.complete(executionId, "FAILED", e.getMessage());
            }
        }, executor);

        activeExecutions.put(executionId, future);
    }

    public void execute(UUID executionId) {
        Execution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new RuntimeException("Execution not found: " + executionId));

        executionService.start(executionId);

        UUID missionId = execution.getMissionId();
        List<MissionFlow> missionFlows = missionFlowRepository.findByIdMissionId(missionId);

        if (missionFlows.isEmpty()) {
            log.warn("No flows configured for mission {}", missionId);
            executionService.complete(executionId, "COMPLETED", null);
            return;
        }

        missionFlows.sort(Comparator.comparingInt(MissionFlow::getExecOrder));

        try {
            // Resolve DAG: build adjacency from exec_order (sequential by default)
            // For each flow in order, get agents and execute them
            String previousOutput = null;
            int globalStepOrder = 0;

            for (MissionFlow mf : missionFlows) {
                UUID flowId = mf.getId().getFlowId();
                List<FlowAgent> flowAgents = flowAgentRepository.findByIdFlowId(flowId);

                if (flowAgents.isEmpty()) {
                    log.warn("No agents assigned to flow {}", flowId);
                    continue;
                }

                // Execute agents within a flow sequentially
                for (FlowAgent fa : flowAgents) {
                    UUID agentId = fa.getId().getAgentId();

                    ExecutionStep step = agentRuntimeService.executeAgentStep(
                        executionId, flowId, agentId, globalStepOrder++,
                        previousOutput, missionId
                    );

                    if ("COMPLETED".equals(step.getStatus())) {
                        previousOutput = step.getOutputData();
                    } else {
                        executionService.complete(executionId, "FAILED",
                            "Step failed: flow=" + flowId + " agent=" + agentId + " error=" + step.getErrorMessage());
                        updateMissionStatus(missionId, "FAILED");
                        return;
                    }
                }
            }

            executionService.complete(executionId, "COMPLETED", null);
            updateMissionStatus(missionId, "COMPLETED");

        } catch (Exception e) {
            log.error("Execution {} failed with exception", executionId, e);
            executionService.complete(executionId, "FAILED", e.getMessage());
            updateMissionStatus(missionId, "FAILED");
        }
    }

    public void cancelExecution(UUID executionId) {
        CompletableFuture<Void> future = activeExecutions.get(executionId);
        if (future != null) {
            future.cancel(true);
            activeExecutions.remove(executionId);
        }
        executionService.complete(executionId, "CANCELLED", "Execution cancelled by user");
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

    private void updateMissionStatus(UUID missionId, String status) {
        missionRepository.findById(missionId).ifPresent(mission -> {
            mission.setStatus(status);
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                mission.setCompletedAt(LocalDateTime.now());
            }
            missionRepository.save(mission);
        });
    }
}
