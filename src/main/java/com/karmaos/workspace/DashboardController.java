package com.karmaos.workspace;

import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class DashboardController {

    private final ProjectRepository projectRepository;
    private final MissionRepository missionRepository;
    private final ArtifactRepository artifactRepository;

    public DashboardController(ProjectRepository projectRepository,
                               MissionRepository missionRepository,
                               ArtifactRepository artifactRepository) {
        this.projectRepository = projectRepository;
        this.missionRepository = missionRepository;
        this.artifactRepository = artifactRepository;
    }

    @GetMapping(ApiConstants.DASHBOARD + "/{workspaceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(@PathVariable UUID workspaceId) {
        long projectCount = projectRepository.countByWorkspaceIdAndStatus(workspaceId, "ACTIVE");
        long missionCount = projectRepository.findByWorkspaceIdAndStatus(workspaceId, "ACTIVE")
                .stream()
                .mapToLong(p -> missionRepository.countByProjectIdAndStatus(p.getId(), "ACTIVE"))
                .sum();
        long artifactCount = artifactRepository.count();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "projectCount", projectCount,
                "missionCount", missionCount,
                "artifactCount", artifactCount,
                "recentActivity", java.util.Collections.emptyList()
        )));
    }
}
