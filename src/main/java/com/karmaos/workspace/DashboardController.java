package com.karmaos.workspace;

import com.karmaos.access.OwnershipService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class DashboardController {

    private final ProjectRepository projectRepository;
    private final MissionRepository missionRepository;
    private final ArtifactRepository artifactRepository;
    private final OwnershipService ownershipService;

    public DashboardController(ProjectRepository projectRepository,
                               MissionRepository missionRepository,
                               ArtifactRepository artifactRepository,
                               OwnershipService ownershipService) {
        this.projectRepository = projectRepository;
        this.missionRepository = missionRepository;
        this.artifactRepository = artifactRepository;
        this.ownershipService = ownershipService;
    }

    @GetMapping(ApiConstants.DASHBOARD + "/{workspaceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal user) {
        ownershipService.getOwnedWorkspace(user.getId(), workspaceId);

        long projectCount = projectRepository.countByWorkspaceIdAndStatus(workspaceId, "ACTIVE");
        List<UUID> missionIds = projectRepository.findByWorkspaceIdAndStatus(workspaceId, "ACTIVE")
                .stream()
                .flatMap(p -> missionRepository.findByProjectIdAndStatusNot(p.getId(), "DELETED").stream())
                .map(Mission::getId)
                .collect(Collectors.toList());
        long missionCount = missionIds.size();
        long artifactCount = missionIds.isEmpty() ? 0 : artifactRepository.countByMissionIdIn(missionIds);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "projectCount", projectCount,
                "missionCount", missionCount,
                "artifactCount", artifactCount,
                "recentActivity", java.util.Collections.emptyList()
        )));
    }
}
