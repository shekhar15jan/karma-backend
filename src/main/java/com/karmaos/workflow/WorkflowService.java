package com.karmaos.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.access.OwnershipService;
import com.karmaos.agent.AgentRepository;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.execution.Execution;
import com.karmaos.execution.ExecutionOrchestrator;
import com.karmaos.flow.FlowResponse;
import com.karmaos.flow.FlowService;
import com.karmaos.junction.MissionFlow;
import com.karmaos.junction.MissionFlowId;
import com.karmaos.junction.MissionFlowRepository;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.Project;
import com.karmaos.project.ProjectRepository;
import com.karmaos.workspace.Workspace;
import com.karmaos.workspace.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkflowService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final ExecutionOrchestrator orchestrator;
    private final MissionRepository missionRepository;
    private final MissionFlowRepository missionFlowRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final OwnershipService ownershipService;
    private final FlowService flowService;

    public WorkflowService(WorkflowDefinitionRepository definitionRepository,
                           WorkflowRunRepository runRepository,
                           ObjectMapper objectMapper,
                           ExecutionOrchestrator orchestrator,
                           MissionRepository missionRepository,
                           MissionFlowRepository missionFlowRepository,
                           WorkspaceRepository workspaceRepository,
                           ProjectRepository projectRepository,
                           AgentRepository agentRepository,
                           OwnershipService ownershipService,
                           FlowService flowService) {
        this.definitionRepository = definitionRepository;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
        this.missionRepository = missionRepository;
        this.missionFlowRepository = missionFlowRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.agentRepository = agentRepository;
        this.ownershipService = ownershipService;
        this.flowService = flowService;
    }

    public List<WorkflowDefinition> listDefinitions(UUID projectId, UUID userId) {
        ownershipService.getOwnedProject(userId, projectId);
        return definitionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional
    public FlowResponse saveDesigner(WorkflowDefinitionRequest request, UUID userId) {
        UUID projectId = request.getProjectId();
        if (projectId != null) {
            ownershipService.getOwnedProject(userId, projectId);
        }
        persistDefinition(request, projectId);
        return flowService.upsertDesignerFlow(request.getName(), request.getDescription(),
            request.getCategory(), request.getNodes(), request.getEdges());
    }

    @Transactional
    public WorkflowDefinition persistDefinition(WorkflowDefinitionRequest request, UUID projectId) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName(request.getName());
        def.setProjectId(projectId);
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
    public WorkflowRunResponse execute(WorkflowDefinitionRequest request, UUID userId) {
        UUID projectId = resolveProjectId(userId);
        validateAgentIds(request.getNodes());

        WorkflowDefinition definition = persistDefinition(request, projectId);
        FlowResponse flowResponse = flowService.upsertDesignerFlow(request.getName(), request.getDescription(),
            request.getCategory(), request.getNodes(), request.getEdges());
        UUID flowId = flowResponse.getId();

        Mission mission = new Mission();
        mission.setProjectId(projectId);
        mission.setName(request.getName());
        mission.setMissionType("WORKFLOW");
        mission.setStatus("DRAFT");
        mission.setProgress(0);
        mission = missionRepository.save(mission);

        MissionFlow mf = new MissionFlow();
        mf.setId(new MissionFlowId(mission.getId(), flowId));
        mf.setExecOrder(0);
        mf.setStatus("ACTIVE");
        missionFlowRepository.save(mf);

        Execution execution = orchestrator.startExecutionAfterCommit(mission.getId());

        WorkflowRun run = new WorkflowRun();
        run.setWorkflowName(request.getName());
        run.setProjectId(projectId);
        run.setExecutionId(execution.getId());
        run.setMissionId(mission.getId());
        run.setFlowId(flowId);
        run.setWorkflowId(definition.getId());
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run = runRepository.save(run);

        return toResponse(run);
    }

    private void validateAgentIds(List<Map<String, Object>> nodes) {
        if (nodes == null) {
            return;
        }
        for (Map<String, Object> node : nodes) {
            Object agentIdObj = node.get("agentId");
            if (agentIdObj == null) {
                continue;
            }
            UUID agentId = UUID.fromString(agentIdObj.toString());
            agentRepository.findByIdAndStatus(agentId, "ACTIVE")
                    .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                            "Agent not found or inactive: " + agentId));
        }
    }

    private UUID resolveProjectId(UUID userId) {
        List<Workspace> workspaces = workspaceRepository.findByOwnerIdAndStatus(userId, "ACTIVE");
        Workspace ws;
        if (workspaces.isEmpty()) {
            ws = new Workspace();
            ws.setName("Default Workspace");
            ws.setOwnerId(userId);
            ws.setStatus("ACTIVE");
            ws = workspaceRepository.save(ws);
        } else {
            ws = workspaces.get(0);
        }

        List<Project> projects = projectRepository.findByWorkspaceIdAndStatus(ws.getId(), "ACTIVE");
        if (!projects.isEmpty()) {
            return projects.get(0).getId();
        }

        Project project = new Project();
        project.setWorkspaceId(ws.getId());
        project.setName("Workflow Designs");
        project.setCategory("WORKFLOW");
        project.setStatus("ACTIVE");
        project = projectRepository.save(project);
        return project.getId();
    }

    public List<WorkflowRunResponse> listRuns(UUID projectId, UUID userId) {
        ownershipService.getOwnedProject(userId, projectId);
        return runRepository.findByProjectIdOrderByStartedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<WorkflowRunResponse> listRecentRuns(UUID userId) {
        List<UUID> projectIds = ownershipService.getOwnedProjectIds(userId);
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return runRepository.findByProjectIdInOrderByStartedAtDesc(projectIds)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<WorkflowDefinition> listAllDefinitions(UUID userId) {
        List<UUID> ownedProjectIds = ownershipService.getOwnedProjectIds(userId);
        if (ownedProjectIds.isEmpty()) {
            return List.of();
        }
        return definitionRepository.findByProjectIdInOrderByCreatedAtDesc(ownedProjectIds);
    }

    @Transactional
    public void deleteDefinition(UUID id, UUID userId) {
        WorkflowDefinition def = definitionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Workflow not found: " + id));
        if (def.getProjectId() != null) {
            ownershipService.getOwnedProject(userId, def.getProjectId());
        }
        definitionRepository.delete(def);
    }

    private WorkflowRunResponse toResponse(WorkflowRun r) {
        return WorkflowRunResponse.builder()
                .id(r.getId())
                .workflowId(r.getWorkflowId())
                .projectId(r.getProjectId())
                .executionId(r.getExecutionId())
                .missionId(r.getMissionId())
                .workflowName(r.getWorkflowName())
                .status(r.getStatus())
                .startedAt(r.getStartedAt())
                .completedAt(r.getCompletedAt())
                .duration(r.getDuration())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
