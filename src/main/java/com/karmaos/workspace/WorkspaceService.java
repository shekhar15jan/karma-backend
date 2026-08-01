package com.karmaos.workspace;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ErrorCode;
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
    private final int maxWorkspacesPerUser;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            ProjectRepository projectRepository,
                            @Value("${app.workspace.max-per-user:10}") int maxWorkspacesPerUser) {
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
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
        return WorkspaceResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .ownerId(w.getOwnerId())
                .status(w.getStatus())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }
}
