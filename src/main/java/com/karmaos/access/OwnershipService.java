package com.karmaos.access;

import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.execution.Execution;
import com.karmaos.execution.ExecutionRepository;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.Project;
import com.karmaos.project.ProjectRepository;
import com.karmaos.workspace.Workspace;
import com.karmaos.workspace.WorkspaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OwnershipService {

    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final MissionRepository missionRepository;
    private final ExecutionRepository executionRepository;
    private final ArtifactRepository artifactRepository;

    public OwnershipService(WorkspaceRepository workspaceRepository,
                            ProjectRepository projectRepository,
                            MissionRepository missionRepository,
                            ExecutionRepository executionRepository,
                            ArtifactRepository artifactRepository) {
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.missionRepository = missionRepository;
        this.executionRepository = executionRepository;
        this.artifactRepository = artifactRepository;
    }

    public Workspace getOwnedWorkspace(UUID userId, UUID workspaceId) {
        return workspaceRepository.findByIdAndOwnerIdAndStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Workspace not found: " + workspaceId));
    }

    public Project getOwnedProject(UUID userId, UUID projectId) {
        Project project = projectRepository.findByIdAndStatus(projectId, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Project not found: " + projectId));
        getOwnedWorkspace(userId, project.getWorkspaceId());
        return project;
    }

    public Mission getOwnedMission(UUID userId, UUID missionId) {
        Mission mission = missionRepository.findByIdAndStatusNot(missionId, "DELETED")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Mission not found: " + missionId));
        getOwnedProject(userId, mission.getProjectId());
        return mission;
    }

    public Execution getOwnedExecution(UUID userId, UUID executionId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution not found: " + executionId));
        if (execution.getMissionId() != null) {
            getOwnedMission(userId, execution.getMissionId());
        }
        return execution;
    }

    public Artifact getOwnedArtifact(UUID userId, UUID artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Artifact not found: " + artifactId));
        getOwnedMission(userId, artifact.getMissionId());
        return artifact;
    }

    public boolean canAccessMission(UUID userId, UUID missionId) {
        try {
            getOwnedMission(userId, missionId);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    public List<UUID> getOwnedWorkspaceIds(UUID userId) {
        return workspaceRepository.findByOwnerIdAndStatus(userId, "ACTIVE").stream()
                .map(Workspace::getId)
                .collect(Collectors.toList());
    }

    public List<UUID> getOwnedProjectIds(UUID userId) {
        List<UUID> workspaceIds = getOwnedWorkspaceIds(userId);
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return projectRepository.findByWorkspaceIdInAndStatus(workspaceIds, "ACTIVE").stream()
                .map(Project::getId)
                .collect(Collectors.toList());
    }

    public List<UUID> getOwnedMissionIds(UUID userId) {
        List<UUID> projectIds = getOwnedProjectIds(userId);
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return missionRepository.findByProjectIdInAndStatusNot(projectIds, "DELETED").stream()
                .map(Mission::getId)
                .collect(Collectors.toList());
    }
}
