package com.karmaos.project;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.mission.MissionRepository;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.workspace.Workspace;
import com.karmaos.workspace.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MissionRepository missionRepository;
    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;

    public ProjectService(ProjectRepository projectRepository,
                          WorkspaceRepository workspaceRepository,
                          MissionRepository missionRepository,
                          ProviderRepository providerRepository,
                          ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.missionRepository = missionRepository;
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
    }

    public List<ProjectResponse> listByWorkspace(UUID workspaceId, UserPrincipal user) {
        getOwnedWorkspace(workspaceId, user.getId());
        return projectRepository.findByWorkspaceIdAndStatus(workspaceId, "ACTIVE")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProjectResponse getById(UUID id, UserPrincipal user) {
        return toResponse(getOwnedProject(id, user.getId()));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request, UserPrincipal user) {
        getOwnedWorkspace(request.getWorkspaceId(), user.getId());
        if (projectRepository.existsByWorkspaceIdAndNameIgnoreCaseAndStatus(
                request.getWorkspaceId(), request.getName(), "ACTIVE")) {
            throw new ApiException(ErrorCode.DUPLICATE_NAME,
                    "Project name already exists in this workspace: " + request.getName());
        }
        validateDefaultProvider(request.getDefaultProviderId());

        Project project = new Project();
        project.setWorkspaceId(request.getWorkspaceId());
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setCategory(request.getCategory());
        project.setBrandProfile(request.getBrandProfile());
        project.setDefaultProviderId(request.getDefaultProviderId());
        try {
            project.setTags(objectMapper.writeValueAsString(request.getTags() != null ? request.getTags() : Collections.emptyList()));
        } catch (Exception e) {
            project.setTags("[]");
        }
        project = projectRepository.save(project);
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse update(UUID id, ProjectRequest request, UserPrincipal user) {
        Project project = getOwnedProject(id, user.getId());
        if (projectRepository.existsByWorkspaceIdAndNameIgnoreCaseAndStatusAndIdNot(
                project.getWorkspaceId(), request.getName(), "ACTIVE", id)) {
            throw new ApiException(ErrorCode.DUPLICATE_NAME,
                    "Project name already exists in this workspace: " + request.getName());
        }
        validateDefaultProvider(request.getDefaultProviderId());

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setCategory(request.getCategory());
        project.setBrandProfile(request.getBrandProfile());
        project.setDefaultProviderId(request.getDefaultProviderId());
        try {
            project.setTags(objectMapper.writeValueAsString(request.getTags() != null ? request.getTags() : Collections.emptyList()));
        } catch (Exception e) {
            project.setTags("[]");
        }
        project = projectRepository.save(project);
        return toResponse(project);
    }

    @Transactional
    public void delete(UUID id, UserPrincipal user) {
        Project project = getOwnedProject(id, user.getId());
        if (missionRepository.countByProjectIdAndStatusNot(id, "DELETED") > 0) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot delete project with active missions. Delete its missions first.");
        }
        project.softDelete();
        projectRepository.save(project);
    }

    private void validateDefaultProvider(UUID providerId) {
        if (providerId == null) {
            return;
        }
        providerRepository.findById(providerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "Provider not found or inactive: " + providerId));
    }

    private Workspace getOwnedWorkspace(UUID workspaceId, UUID ownerId) {
        return workspaceRepository.findByIdAndOwnerIdAndStatus(workspaceId, ownerId, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Workspace not found: " + workspaceId));
    }

    private Project getOwnedProject(UUID id, UUID ownerId) {
        Project project = projectRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Project not found: " + id));
        getOwnedWorkspace(project.getWorkspaceId(), ownerId);
        return project;
    }

    private ProjectResponse toResponse(Project p) {
        List<String> tags = Collections.emptyList();
        try {
            tags = objectMapper.readValue(p.getTags(), new TypeReference<List<String>>() {});
        } catch (Exception ignored) {}

        return ProjectResponse.builder()
                .id(p.getId())
                .workspaceId(p.getWorkspaceId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .tags(tags)
                .brandProfile(p.getBrandProfile())
                .defaultProviderId(p.getDefaultProviderId())
                .missionCount(missionRepository.countByProjectIdAndStatusNot(p.getId(), "DELETED"))
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
