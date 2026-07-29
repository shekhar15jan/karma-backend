package com.karmaos.project;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public ProjectService(ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    public List<ProjectResponse> listByWorkspace(UUID workspaceId) {
        return projectRepository.findByWorkspaceIdAndStatus(workspaceId, "ACTIVE")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProjectResponse getById(UUID id) {
        Project project = projectRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Project not found: " + id));
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
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
    public ProjectResponse update(UUID id, ProjectRequest request) {
        Project project = projectRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Project not found: " + id));
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
    public void delete(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Project not found: " + id));
        project.softDelete();
        projectRepository.save(project);
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
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
