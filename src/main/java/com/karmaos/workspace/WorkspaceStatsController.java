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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

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
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(@PathVariable UUID id) {
        workspaceService.getById(id);
        long projectCount = projectRepository.countByWorkspaceIdAndStatus(id, "ACTIVE");
        long missionCount = projectRepository.findByWorkspaceIdAndStatus(id, "ACTIVE")
                .stream()
                .mapToLong(p -> missionRepository.countByProjectIdAndStatus(p.getId(), "ACTIVE"))
                .sum();
        long artifactCount = artifactRepository.count();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "projectCount", projectCount,
                "missionCount", missionCount,
                "artifactCount", artifactCount,
                "lastActivity", LocalDateTime.now()
        )));
    }
}
