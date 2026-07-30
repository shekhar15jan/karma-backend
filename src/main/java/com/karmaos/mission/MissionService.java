package com.karmaos.mission;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.execution.ExecutionOrchestrator;
import com.karmaos.flow.FlowRepository;
import com.karmaos.junction.MissionFlow;
import com.karmaos.junction.MissionFlowId;
import com.karmaos.junction.MissionFlowRepository;
import com.karmaos.execution.Execution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionFlowRepository missionFlowRepository;
    private final FlowRepository flowRepository;
    private final ExecutionOrchestrator orchestrator;

    private static final Map<String, String> MISSION_TYPE_TO_FLOW_NAME = Map.of(
        "VIDEO", "Video Generation Flow",
        "BLOG", "Blog Generation Flow",
        "SCRIPT", "Script Generation Flow"
    );

    public MissionService(MissionRepository missionRepository,
                          MissionFlowRepository missionFlowRepository,
                          FlowRepository flowRepository,
                          ExecutionOrchestrator orchestrator) {
        this.missionRepository = missionRepository;
        this.missionFlowRepository = missionFlowRepository;
        this.flowRepository = flowRepository;
        this.orchestrator = orchestrator;
    }

    public List<MissionResponse> listByProject(UUID projectId) {
        return missionRepository.findByProjectId(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public MissionResponse getById(UUID id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Mission not found: " + id));
        return toResponse(mission);
    }

    @Transactional
    public MissionResponse create(MissionRequest request) {
        Mission mission = new Mission();
        mission.setProjectId(request.getProjectId());
        mission.setName(request.getName());
        mission.setDescription(request.getDescription());
        mission.setMissionType(request.getMissionType() != null ? request.getMissionType() : "VIDEO");
        mission.setPriority(request.getPriority() != null ? request.getPriority() : "MEDIUM");
        mission.setProviderId(request.getProviderId());
        mission.setStatus("DRAFT");
        mission.setProgress(0);
        mission = missionRepository.save(mission);

        linkFlowsToMission(mission, request);

        return toResponse(mission);
    }

    private void linkFlowsToMission(Mission mission, MissionRequest request) {
        List<UUID> selectedFlowIds = request.getSelectedFlowIds();

        if (selectedFlowIds != null && !selectedFlowIds.isEmpty()) {
            for (int i = 0; i < selectedFlowIds.size(); i++) {
                MissionFlow mf = new MissionFlow();
                mf.setId(new MissionFlowId(mission.getId(), selectedFlowIds.get(i)));
                mf.setExecOrder(i);
                mf.setStatus("ACTIVE");
                missionFlowRepository.save(mf);
            }
        } else {
            String flowName = MISSION_TYPE_TO_FLOW_NAME.get(mission.getMissionType());
            if (flowName != null) {
                flowRepository.findByNameContainingIgnoreCase(flowName).stream()
                    .findFirst()
                    .ifPresent(flow -> {
                        MissionFlow mf = new MissionFlow();
                        mf.setId(new MissionFlowId(mission.getId(), flow.getId()));
                        mf.setExecOrder(1);
                        mf.setStatus("ACTIVE");
                        missionFlowRepository.save(mf);
                    });
            }
        }
    }

    @Transactional
    public MissionResponse update(UUID id, MissionRequest request) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Mission not found: " + id));
        mission.setName(request.getName());
        mission.setDescription(request.getDescription());
        mission.setMissionType(request.getMissionType() != null ? request.getMissionType() : mission.getMissionType());
        mission.setPriority(request.getPriority() != null ? request.getPriority() : mission.getPriority());
        mission.setProviderId(request.getProviderId());
        mission = missionRepository.save(mission);
        return toResponse(mission);
    }

    @Transactional
    public void delete(UUID id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Mission not found: " + id));
        mission.softDelete();
        missionRepository.save(mission);
    }

    @Transactional
    public MissionResponse execute(UUID id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Mission not found: " + id));
        mission.setStatus("RUNNING");
        mission.setStartedAt(LocalDateTime.now());
        mission.setProgress(0);
        mission = missionRepository.save(mission);

        Execution execution = orchestrator.startExecution(mission.getId());
        orchestrator.executeAsync(execution.getId());

        return toResponse(mission);
    }

    private MissionResponse toResponse(Mission m) {
        return MissionResponse.builder()
                .id(m.getId())
                .projectId(m.getProjectId())
                .name(m.getName())
                .description(m.getDescription())
                .missionType(m.getMissionType())
                .status(m.getStatus())
                .priority(m.getPriority())
                .providerId(m.getProviderId())
                .progress(m.getProgress())
                .startedAt(m.getStartedAt())
                .completedAt(m.getCompletedAt())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
