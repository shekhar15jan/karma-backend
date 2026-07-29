package com.karmaos.mission;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MissionService {

    private final MissionRepository missionRepository;

    public MissionService(MissionRepository missionRepository) {
        this.missionRepository = missionRepository;
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
        return toResponse(mission);
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
