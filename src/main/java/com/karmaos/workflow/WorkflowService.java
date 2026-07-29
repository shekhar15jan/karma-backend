package com.karmaos.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkflowService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public WorkflowService(WorkflowDefinitionRepository definitionRepository,
                           WorkflowRunRepository runRepository,
                           ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    public List<WorkflowDefinition> listDefinitions(UUID projectId) {
        return definitionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional
    public WorkflowDefinition createDefinition(WorkflowDefinitionRequest request) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName(request.getName());
        try {
            def.setNodes(objectMapper.writeValueAsString(request.getNodes()));
            def.setEdges(objectMapper.writeValueAsString(request.getEdges()));
        } catch (Exception e) {
            def.setNodes("[]");
            def.setEdges("[]");
        }
        return definitionRepository.save(def);
    }

    @Transactional
    public WorkflowRunResponse execute(String workflowName, UUID projectId) {
        WorkflowRun run = new WorkflowRun();
        run.setWorkflowName(workflowName);
        run.setProjectId(projectId);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run = runRepository.save(run);
        return toResponse(run);
    }

    public List<WorkflowRunResponse> listRuns(UUID projectId) {
        return runRepository.findByProjectIdOrderByStartedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<WorkflowRunResponse> listRecentRuns() {
        return runRepository.findByStatus("RUNNING")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private WorkflowRunResponse toResponse(WorkflowRun r) {
        return WorkflowRunResponse.builder()
                .id(r.getId())
                .workflowId(r.getWorkflowId())
                .projectId(r.getProjectId())
                .workflowName(r.getWorkflowName())
                .status(r.getStatus())
                .startedAt(r.getStartedAt())
                .completedAt(r.getCompletedAt())
                .duration(r.getDuration())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
