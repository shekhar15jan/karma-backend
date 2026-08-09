package com.karmaos.mission;

import com.karmaos.access.OwnershipService;
import com.karmaos.artifact.ArtifactStorageService;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.execution.Execution;
import com.karmaos.execution.ExecutionOrchestrator;
import com.karmaos.execution.ExecutionRepository;
import com.karmaos.flow.Flow;
import com.karmaos.flow.FlowRepository;
import com.karmaos.junction.MissionFlow;
import com.karmaos.junction.MissionFlowId;
import com.karmaos.junction.MissionFlowRepository;
import com.karmaos.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionFlowRepository missionFlowRepository;
    private final FlowRepository flowRepository;
    private final ExecutionOrchestrator orchestrator;
    private final ExecutionRepository executionRepository;
    private final OwnershipService ownershipService;
    private final ProviderRepository providerRepository;
    private final ArtifactStorageService artifactStorageService;

    private static final Set<String> ALLOWED_MISSION_TYPES =
            Set.of("VIDEO", "BLOG", "SCRIPT", "VOICE", "THUMBNAIL", "WORKFLOW");
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    public MissionService(MissionRepository missionRepository,
                          MissionFlowRepository missionFlowRepository,
                          FlowRepository flowRepository,
                          ExecutionOrchestrator orchestrator,
ExecutionRepository executionRepository,
                           OwnershipService ownershipService,
                           ProviderRepository providerRepository,
                           ArtifactStorageService artifactStorageService) {
        this.missionRepository = missionRepository;
        this.missionFlowRepository = missionFlowRepository;
        this.flowRepository = flowRepository;
        this.orchestrator = orchestrator;
        this.executionRepository = executionRepository;
        this.ownershipService = ownershipService;
        this.providerRepository = providerRepository;
        this.artifactStorageService = artifactStorageService;
    }

    public List<MissionResponse> listByProject(UUID projectId, UserPrincipal user) {
        ownershipService.getOwnedProject(user.getId(), projectId);
        return missionRepository.findByProjectIdAndStatusNot(projectId, "DELETED")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public MissionResponse getById(UUID id, UserPrincipal user) {
        return toResponse(ownershipService.getOwnedMission(user.getId(), id));
    }

    @Transactional
    public MissionResponse create(MissionRequest request, UserPrincipal user) {
        ownershipService.getOwnedProject(user.getId(), request.getProjectId());
        validateProvider(request.getProviderId());

        Mission mission = new Mission();
        mission.setProjectId(request.getProjectId());
        mission.setName(request.getName());
        mission.setDescription(request.getDescription());
        mission.setMissionType(normalizeType(request.getMissionType()));
        mission.setPriority(normalizePriority(request.getPriority()));
        mission.setProviderId(request.getProviderId());
        mission.setOutputDirectory(artifactStorageService.normalizeFolder(request.getOutputDirectory()));
        mission.setTargetDurationSeconds(normalizeDuration(request.getTargetDurationSeconds()));
        mission.setStatus("DRAFT");
        mission.setProgress(0);
        mission = missionRepository.save(mission);

        linkFlowsToMission(mission, request);

        return toResponse(mission);
    }

    private void validateProvider(UUID providerId) {
        if (providerId == null) {
            return;
        }
        providerRepository.findById(providerId)
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
                .orElseThrow(() -> ErrorCode.BAD_REQUEST.exception("Provider not found, deleted, or publishing-only: " + providerId));
    }

    private String normalizeType(String missionType) {
        if (missionType == null || missionType.isBlank()) {
            return "VIDEO";
        }
        String type = missionType.trim().toUpperCase();
        if (!ALLOWED_MISSION_TYPES.contains(type)) {
            throw ErrorCode.BAD_REQUEST.exception("Unsupported mission type: " + missionType);
        }
        return type;
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "MEDIUM";
        }
        String p = priority.trim().toUpperCase();
        if (!ALLOWED_PRIORITIES.contains(p)) {
            throw ErrorCode.BAD_REQUEST.exception("Unsupported priority: " + priority);
        }
        return p;
    }

    private Integer normalizeDuration(Integer targetDurationSeconds) {
        if (targetDurationSeconds == null || targetDurationSeconds <= 0) {
            return null;
        }
        return Math.max(30, targetDurationSeconds);
    }

    private void linkFlowsToMission(Mission mission, MissionRequest request) {
        List<UUID> selectedFlowIds = request.getSelectedFlowIds();

        if (selectedFlowIds == null || selectedFlowIds.isEmpty()) {
            List<Flow> defaultFlows = flowRepository.findByCategoryAndStatus(mission.getMissionType(), "ACTIVE");
            if (!defaultFlows.isEmpty()) {
                Flow defaultFlow = defaultFlows.get(0);
                MissionFlow mf = new MissionFlow();
                mf.setId(new MissionFlowId(mission.getId(), defaultFlow.getId()));
                mf.setExecOrder(0);
                mf.setStatus("ACTIVE");
                missionFlowRepository.save(mf);
            }
            return;
        }

        Set<UUID> seen = new LinkedHashSet<>();
        int order = 0;
        for (UUID flowId : selectedFlowIds) {
            if (flowId == null || !seen.add(flowId)) {
                continue;
            }
            if (flowRepository.findByIdAndStatus(flowId, "ACTIVE").isEmpty()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Flow not found or inactive: " + flowId);
            }
            MissionFlow mf = new MissionFlow();
            mf.setId(new MissionFlowId(mission.getId(), flowId));
            mf.setExecOrder(order++);
            mf.setStatus("ACTIVE");
            missionFlowRepository.save(mf);
        }
    }

    @Transactional
    public MissionResponse update(UUID id, MissionRequest request, UserPrincipal user) {
        Mission mission = ownershipService.getOwnedMission(user.getId(), id);
        mission.setName(request.getName());
        mission.setDescription(request.getDescription());
        if (request.getMissionType() != null && !request.getMissionType().isBlank()) {
            mission.setMissionType(normalizeType(request.getMissionType()));
        }
        if (request.getPriority() != null && !request.getPriority().isBlank()) {
            mission.setPriority(normalizePriority(request.getPriority()));
        }
        validateProvider(request.getProviderId());
        mission.setProviderId(request.getProviderId());
        mission.setOutputDirectory(artifactStorageService.normalizeFolder(request.getOutputDirectory()));
        mission.setTargetDurationSeconds(normalizeDuration(request.getTargetDurationSeconds()));
        mission = missionRepository.save(mission);
        relinkFlowsToMission(mission, request);
        return toResponse(mission);
    }

    private void relinkFlowsToMission(Mission mission, MissionRequest request) {
        missionFlowRepository.deleteByIdMissionId(mission.getId());
        linkFlowsToMission(mission, request);
    }

    @Transactional
    public void delete(UUID id, UserPrincipal user) {
        Mission mission = ownershipService.getOwnedMission(user.getId(), id);
        if ("RUNNING".equalsIgnoreCase(mission.getStatus()) || "WAITING".equalsIgnoreCase(mission.getStatus())) {
            cancelActiveExecutions(mission.getId());
        }
        missionFlowRepository.deleteByIdMissionId(mission.getId());
        mission.setStatus("DELETED");
        mission.setDeletedAt(LocalDateTime.now());
        missionRepository.save(mission);
    }

    private void cancelActiveExecutions(UUID missionId) {
        executionRepository.findByMissionIdOrderByCreatedAtDesc(missionId).stream()
                .filter(e -> "RUNNING".equalsIgnoreCase(e.getStatus())
                        || "PENDING".equalsIgnoreCase(e.getStatus())
                        || "WAITING".equalsIgnoreCase(e.getStatus()))
                .forEach(e -> orchestrator.cancelExecution(e.getId()));
    }

    @Transactional
    public MissionResponse execute(UUID id, UserPrincipal user) {
        Mission mission = ownershipService.getOwnedMission(user.getId(), id);
        if ("RUNNING".equalsIgnoreCase(mission.getStatus()) || "WAITING".equalsIgnoreCase(mission.getStatus())) {
            throw ErrorCode.CONFLICT.exception("Mission is already running or awaiting review: " + id);
        }
        mission.setStatus("RUNNING");
        mission.setStartedAt(LocalDateTime.now());
        mission.setProgress(0);
        mission = missionRepository.save(mission);

        orchestrator.startExecutionAfterCommit(mission.getId());

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
                .outputDirectory(m.getOutputDirectory())
                .targetDurationSeconds(m.getTargetDurationSeconds())
                .startedAt(m.getStartedAt())
                .completedAt(m.getCompletedAt())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
