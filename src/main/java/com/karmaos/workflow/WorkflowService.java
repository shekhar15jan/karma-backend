package com.karmaos.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.execution.ExecutionOrchestrator;
import com.karmaos.flow.Flow;
import com.karmaos.flow.FlowRepository;
import com.karmaos.junction.FlowAgent;
import com.karmaos.junction.FlowAgentId;
import com.karmaos.junction.FlowAgentRepository;
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
    private final FlowRepository flowRepository;
    private final FlowAgentRepository flowAgentRepository;
    private final MissionFlowRepository missionFlowRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;

    public WorkflowService(WorkflowDefinitionRepository definitionRepository,
                           WorkflowRunRepository runRepository,
                           ObjectMapper objectMapper,
                           ExecutionOrchestrator orchestrator,
                           MissionRepository missionRepository,
                           FlowRepository flowRepository,
                           FlowAgentRepository flowAgentRepository,
                           MissionFlowRepository missionFlowRepository,
                           WorkspaceRepository workspaceRepository,
                           ProjectRepository projectRepository) {
        this.definitionRepository = definitionRepository;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
        this.orchestrator = orchestrator;
        this.missionRepository = missionRepository;
        this.flowRepository = flowRepository;
        this.flowAgentRepository = flowAgentRepository;
        this.missionFlowRepository = missionFlowRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
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
    public WorkflowRunResponse execute(WorkflowDefinitionRequest request, UUID userId) {
        UUID projectId = resolveProjectId(userId);

        Mission mission = new Mission();
        mission.setProjectId(projectId);
        mission.setName(request.getName());
        mission.setMissionType("WORKFLOW");
        mission.setStatus("DRAFT");
        mission.setProgress(0);
        mission = missionRepository.save(mission);

        Flow flow = new Flow();
        flow.setName(request.getName() + " - " + LocalDateTime.now().toString());
        flow.setCategory("DESIGNER");
        flow.setEnabled(true);
        flow.setExecutionOrder(0);
        flow = flowRepository.save(flow);

        List<Map<String, Object>> nodes = request.getNodes();
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                Object agentIdObj = nodes.get(i).get("agentId");
                if (agentIdObj != null) {
                    UUID agentId = UUID.fromString(agentIdObj.toString());
                    FlowAgent fa = new FlowAgent();
                    fa.setId(new FlowAgentId(flow.getId(), agentId));
                    fa.setStepOrder(i);
                    flowAgentRepository.save(fa);
                }
            }
        }

        MissionFlow mf = new MissionFlow();
        mf.setId(new MissionFlowId(mission.getId(), flow.getId()));
        mf.setExecOrder(0);
        mf.setStatus("ACTIVE");
        missionFlowRepository.save(mf);

        com.karmaos.execution.Execution execution = orchestrator.startExecution(mission.getId());
        orchestrator.executeAsync(execution.getId());

        WorkflowRun run = new WorkflowRun();
        run.setWorkflowName(request.getName());
        run.setProjectId(projectId);
        run.setExecutionId(execution.getId());
        run.setMissionId(mission.getId());
        run.setFlowId(flow.getId());
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run = runRepository.save(run);

        return toResponse(run);
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
