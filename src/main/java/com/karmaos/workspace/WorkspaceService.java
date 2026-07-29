package com.karmaos.workspace;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public List<WorkspaceResponse> listByUser(UserPrincipal user) {
        return workspaceRepository.findByOwnerIdAndStatus(user.getId(), "ACTIVE")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public WorkspaceResponse getById(UUID id) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Workspace not found: " + id));
        return toResponse(workspace);
    }

    @Transactional
    public WorkspaceResponse create(WorkspaceRequest request, UserPrincipal user) {
        Workspace workspace = new Workspace();
        workspace.setName(request.getName());
        workspace.setDescription(request.getDescription());
        workspace.setOwnerId(user.getId());
        workspace = workspaceRepository.save(workspace);
        return toResponse(workspace);
    }

    @Transactional
    public WorkspaceResponse update(UUID id, WorkspaceRequest request) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Workspace not found: " + id));
        workspace.setName(request.getName());
        workspace.setDescription(request.getDescription());
        workspace = workspaceRepository.save(workspace);
        return toResponse(workspace);
    }

    @Transactional
    public void delete(UUID id) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Workspace not found: " + id));
        workspace.softDelete();
        workspaceRepository.save(workspace);
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
