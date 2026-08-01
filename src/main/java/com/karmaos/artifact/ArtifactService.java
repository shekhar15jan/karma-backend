package com.karmaos.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.access.OwnershipService;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.execution.ExecutionOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;
    private final ExecutionOrchestrator executionOrchestrator;
    private final OwnershipService ownershipService;

    public ArtifactService(ArtifactRepository artifactRepository, ObjectMapper objectMapper,
                           ExecutionOrchestrator executionOrchestrator, OwnershipService ownershipService) {
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
        this.executionOrchestrator = executionOrchestrator;
        this.ownershipService = ownershipService;
    }

    public List<ArtifactResponse> listAll(UUID userId) {
        return artifactRepository.findByMissionIdInOrderByCreatedAtDesc(ownershipService.getOwnedMissionIds(userId))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ArtifactResponse> listByMission(UUID missionId, UserPrincipal user) {
        ownershipService.getOwnedMission(user.getId(), missionId);
        return artifactRepository.findByMissionIdOrderByCreatedAtDesc(missionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ArtifactResponse getById(UUID id, UserPrincipal user) {
        return toResponse(ownershipService.getOwnedArtifact(user.getId(), id));
    }

    public List<ArtifactResponse> listPendingReview(UUID userId) {
        return artifactRepository.findByMissionIdInOrderByCreatedAtDesc(ownershipService.getOwnedMissionIds(userId))
                .stream()
                .filter(a -> "PENDING".equals(a.getReviewStatus()) && a.getExecutionStepId() == null)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ArtifactResponse updateReviewStatus(UUID id, String status, UserPrincipal user) {
        return updateReviewStatus(id, status, null, user);
    }

    @Transactional
    public ArtifactResponse updateReviewStatus(UUID id, String status, String feedback, UserPrincipal user) {
        Artifact artifact = ownershipService.getOwnedArtifact(user.getId(), id);
        artifact.setReviewStatus(status);
        artifact = artifactRepository.save(artifact);
        if ("APPROVED".equals(status)) {
            executionOrchestrator.onArtifactApproved(id);
        } else if ("REJECTED".equals(status)) {
            executionOrchestrator.onArtifactRejected(id, feedback);
        }
        return toResponse(artifact);
    }

    private ArtifactResponse toResponse(Artifact a) {
        List<String> tags = Collections.emptyList();
        try {
            tags = objectMapper.readValue(a.getTags(), new TypeReference<List<String>>() {});
        } catch (Exception ignored) {}

        return ArtifactResponse.builder()
                .id(a.getId())
                .missionId(a.getMissionId())
                .executionStepId(a.getExecutionStepId())
                .name(a.getName())
                .artifactType(a.getArtifactType())
                .status(a.getStatus())
                .contentText(a.getContentText())
                .contentType(a.getContentType())
                .reviewStatus(a.getReviewStatus())
                .publicationStatus(a.getPublicationStatus())
                .tags(tags)
                .currentVersion(a.getCurrentVersion())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
