package com.karmaos.workspace;

import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.artifact.ArtifactStorageService;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ErrorCode;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final MissionRepository missionRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactStorageService artifactStorageService;
    private final int maxWorkspacesPerUser;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            ProjectRepository projectRepository,
                            MissionRepository missionRepository,
                            ArtifactRepository artifactRepository,
                            ArtifactStorageService artifactStorageService,
                            @Value("${app.workspace.max-per-user:10}") int maxWorkspacesPerUser) {
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.missionRepository = missionRepository;
        this.artifactRepository = artifactRepository;
        this.artifactStorageService = artifactStorageService;
        this.maxWorkspacesPerUser = maxWorkspacesPerUser;
    }

    public List<WorkspaceResponse> listByUser(UserPrincipal user) {
        return workspaceRepository.findByOwnerIdAndStatus(user.getId(), "ACTIVE")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public WorkspaceResponse getById(UUID id, UserPrincipal user) {
        return toResponse(getOwnedWorkspace(id, user.getId()));
    }

    @Transactional
    public WorkspaceResponse create(WorkspaceRequest request, UserPrincipal user) {
        if (workspaceRepository.countActiveByOwnerId(user.getId()) >= maxWorkspacesPerUser) {
            throw ErrorCode.WORKSPACE_LIMIT.exception("Workspace limit reached (" + maxWorkspacesPerUser + ")");
        }
        if (workspaceRepository.existsByOwnerIdAndNameIgnoreCaseAndStatus(user.getId(), request.getName(), "ACTIVE")) {
            throw ErrorCode.DUPLICATE_NAME.exception("A workspace named \"" + request.getName() + "\" already exists");
        }

        Workspace workspace = new Workspace();
        workspace.setName(request.getName());
        workspace.setDescription(request.getDescription());
        workspace.setDefaultOutputDirectory(artifactStorageService.normalizeFolder(request.getDefaultOutputDirectory()));
        workspace.setOwnerId(user.getId());
        workspace = workspaceRepository.save(workspace);
        return toResponse(workspace);
    }

    @Transactional
    public WorkspaceResponse update(UUID id, WorkspaceRequest request, UserPrincipal user) {
        Workspace workspace = getOwnedWorkspace(id, user.getId());
        if (workspaceRepository.existsByOwnerIdAndNameIgnoreCaseAndStatusAndIdNot(user.getId(), request.getName(), "ACTIVE", id)) {
            throw ErrorCode.DUPLICATE_NAME.exception("A workspace named \"" + request.getName() + "\" already exists");
        }
        workspace.setName(request.getName());
        workspace.setDescription(request.getDescription());
        workspace.setDefaultOutputDirectory(artifactStorageService.normalizeFolder(request.getDefaultOutputDirectory()));
        workspace = workspaceRepository.save(workspace);
        return toResponse(workspace);
    }

    @Transactional
    public void delete(UUID id, UserPrincipal user) {
        Workspace workspace = getOwnedWorkspace(id, user.getId());
        if (projectRepository.countByWorkspaceIdAndStatus(id, "ACTIVE") > 0) {
            throw ErrorCode.CONFLICT.exception("Workspace has active projects. Delete its projects first.");
        }
        workspace.softDelete();
        workspaceRepository.save(workspace);
    }

    private Workspace getOwnedWorkspace(UUID id, UUID ownerId) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Workspace not found: " + id));
        if (!"ACTIVE".equals(workspace.getStatus()) || !workspace.getOwnerId().equals(ownerId)) {
            throw ErrorCode.NOT_FOUND.exception("Workspace not found: " + id);
        }
        return workspace;
    }

    private WorkspaceResponse toResponse(Workspace w) {
        List<UUID> projectIds = projectRepository.findByWorkspaceIdAndStatus(w.getId(), "ACTIVE")
                .stream()
                .map(p -> p.getId())
                .collect(Collectors.toList());
        List<UUID> missionIds = projectIds.isEmpty()
                ? List.of()
                : missionRepository.findByProjectIdInAndStatusNot(projectIds, "DELETED")
                        .stream()
                        .map(Mission::getId)
                        .collect(Collectors.toList());

        return WorkspaceResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .ownerId(w.getOwnerId())
                .status(w.getStatus())
                .defaultOutputDirectory(w.getDefaultOutputDirectory())
                .projectCount(projectIds.size())
                .missionCount(missionIds.size())
                .artifactCount(missionIds.isEmpty() ? 0 : artifactRepository.countByMissionIdIn(missionIds))
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }
}
