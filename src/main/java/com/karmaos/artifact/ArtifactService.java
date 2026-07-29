package com.karmaos.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
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

    public ArtifactService(ArtifactRepository artifactRepository, ObjectMapper objectMapper) {
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
    }

    public List<ArtifactResponse> listByMission(UUID missionId) {
        return artifactRepository.findByMissionIdOrderByCreatedAtDesc(missionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ArtifactResponse getById(Long id) {
        Artifact artifact = artifactRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Artifact not found: " + id));
        return toResponse(artifact);
    }

    public List<ArtifactResponse> listPendingReview() {
        return artifactRepository.findByReviewStatus("PENDING")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ArtifactResponse updateReviewStatus(Long id, String status) {
        Artifact artifact = artifactRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Artifact not found: " + id));
        artifact.setReviewStatus(status);
        artifact = artifactRepository.save(artifact);
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
