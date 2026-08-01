package com.karmaos.workspace;

import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class WorkspaceStatsController {

    private final WorkspaceService workspaceService;
    private final ProjectRepository projectRepository;
    private final MissionRepository missionRepository;
    private final ArtifactRepository artifactRepository;

    public WorkspaceStatsController(WorkspaceService workspaceService,
                                    ProjectRepository projectRepository,
                                    MissionRepository missionRepository,
                                    ArtifactRepository artifactRepository) {
        this.workspaceService = workspaceService;
        this.projectRepository = projectRepository;
        this.missionRepository = missionRepository;
        this.artifactRepository = artifactRepository;
    }

    @GetMapping(ApiConstants.WORKSPACES + "/{id}/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        workspaceService.getById(id, user);
        long projectCount = projectRepository.countByWorkspaceIdAndStatus(id, "ACTIVE");
        List<Mission> missions = projectRepository.findByWorkspaceIdAndStatus(id, "ACTIVE")
                .stream()
                .flatMap(p -> missionRepository.findByProjectIdAndStatusNot(p.getId(), "DELETED").stream())
                .collect(Collectors.toList());
        long missionCount = missions.size();
        List<UUID> missionIds = missions.stream().map(Mission::getId).collect(Collectors.toList());
        long artifactCount = missionIds.isEmpty() ? 0 : artifactRepository.countByMissionIdIn(missionIds);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "projectCount", projectCount,
                "missionCount", missionCount,
                "artifactCount", artifactCount,
                "lastActivity", LocalDateTime.now()
        )));
    }
}
