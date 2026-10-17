package com.karmaos.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.access.OwnershipService;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.execution.ExecutionOrchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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
    private final Path mediaRoot;

    public ArtifactService(ArtifactRepository artifactRepository, ObjectMapper objectMapper,
                           ExecutionOrchestrator executionOrchestrator, OwnershipService ownershipService,
                           @Value("${app.storage.location:./data/artifacts}") String storageLocation) {
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
        this.executionOrchestrator = executionOrchestrator;
        this.ownershipService = ownershipService;
        this.mediaRoot = Path.of(storageLocation).toAbsolutePath().normalize().resolve("media");
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

    public ArtifactContent loadContent(UUID id, UserPrincipal user) {
        Artifact artifact = ownershipService.getOwnedArtifact(user.getId(), id);
        return resolveContent(artifact);
    }

    private ArtifactContent resolveContent(Artifact artifact) {
        byte[] bytes = null;
        String contentType = artifact.getContentType();

        // Only storageLocation is trusted to reference binary content; contentText is model-written text.
        String location = artifact.getStorageLocation();
        if (location != null && !location.isBlank()) {
            if (location.startsWith("data:")) {
                int comma = location.indexOf(',');
                if (comma >= 0) {
                    String header = location.substring(0, comma);
                    String base64 = location.substring(comma + 1);
                    try {
                        bytes = Base64.getDecoder().decode(base64);
                        if (header.contains(";")) {
                            String declared = header.substring(5, header.indexOf(';'));
                            if (!declared.isBlank()) contentType = declared;
                        }
                    } catch (IllegalArgumentException e) {
                        bytes = null;
                    }
                }
            } else if (location.startsWith("file:")) {
                try {
                    Path path = Path.of(URI.create(location)).toAbsolutePath().normalize();
                    if (isServableMediaFile(path)) {
                        bytes = Files.readAllBytes(path);
                    }
                } catch (IOException | IllegalArgumentException e) {
                    bytes = null;
                }
            }
        }

        if (bytes == null) {
            String text = artifact.getContentText();
            bytes = text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
            String fallback = "text/markdown";
            if (contentType != null && !contentType.isBlank()
                    && !contentType.toLowerCase().startsWith("text/")) {
                fallback = "text/plain";
            }
            contentType = fallback;
        }

        String filename = artifact.getName();
        String extension = extensionFor(contentType);
        if (filename == null || filename.isBlank()) {
            filename = artifact.getId() != null ? artifact.getId().toString() : "artifact";
        }
        if (!filename.toLowerCase().endsWith("." + extension)) {
            filename = filename + "." + extension;
        }

        return new ArtifactContent(bytes, contentType, filename);
    }

    /** Media files live under the storage media dir; legacy FFmpeg videos live in karma-video-* temp dirs. */
    private boolean isServableMediaFile(Path path) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) return false;
        if (path.startsWith(mediaRoot)) return true;
        Path tmp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Path parent = path.getParent();
        return parent != null && tmp.equals(parent.getParent())
                && parent.getFileName().toString().startsWith("karma-video-")
                && path.getFileName().toString().equals("output.mp4");
    }

    private String extensionFor(String contentType) {
        if (contentType == null) return "bin";
        switch (contentType.toLowerCase()) {
            case "video/mp4": return "mp4";
            case "video/webm": return "webm";
            case "audio/mpeg": return "mp3";
            case "audio/wav": return "wav";
            case "audio/ogg": return "ogg";
            case "image/png": return "png";
            case "image/jpeg": return "jpg";
            case "image/webp": return "webp";
            case "application/json": return "json";
            case "text/html": return "html";
            case "text/plain": return "txt";
            case "text/csv": return "csv";
            default: return "md";
        }
    }

    public record ArtifactContent(byte[] bytes, String contentType, String filename) {}

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
                .storageLocation(a.getStorageLocation())
                .reviewStatus(a.getReviewStatus())
                .publicationStatus(a.getPublicationStatus())
                .tags(tags)
                .currentVersion(a.getCurrentVersion())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
