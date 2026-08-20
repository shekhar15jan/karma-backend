package com.karmaos.artifact;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.ARTIFACTS)
public class ArtifactController {

    private final ArtifactService artifactService;
    private final ArtifactStorageService artifactStorageService;

    public ArtifactController(ArtifactService artifactService, ArtifactStorageService artifactStorageService) {
        this.artifactService = artifactService;
        this.artifactStorageService = artifactStorageService;
    }

    @GetMapping("/storage-root")
    public ResponseEntity<ApiResponse<Map<String, String>>> storageRoot() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "hostRoot", artifactStorageService.getHostRoot(),
                "containerRoot", artifactStorageService.getStorageRoot())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArtifactResponse>>> list(
            @RequestParam Optional<UUID> missionId,
            @AuthenticationPrincipal UserPrincipal user) {
        if (missionId.isPresent()) {
            return ResponseEntity.ok(ApiResponse.ok(artifactService.listByMission(missionId.get(), user)));
        }
        return ResponseEntity.ok(ApiResponse.ok(artifactService.listAll(user.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtifactResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.getById(id, user)));
    }

    @GetMapping("/pending-review")
    public ResponseEntity<ApiResponse<List<ArtifactResponse>>> pendingReview(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.listPendingReview(user.getId())));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<ByteArrayResource> file(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean download,
            @AuthenticationPrincipal UserPrincipal user) {
        ArtifactService.ArtifactContent content = artifactService.loadContent(id, user);
        ByteArrayResource resource = new ByteArrayResource(content.bytes());
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String encodedName = URLEncoder.encode(content.filename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String disposition = download
                ? "attachment; filename*=UTF-8''" + encodedName
                : "inline; filename*=UTF-8''" + encodedName;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<ApiResponse<ArtifactResponse>> updateReviewStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.updateReviewStatus(id, status, user)));
    }

    @GetMapping("/stream")
    public ResponseEntity<org.springframework.core.io.Resource> streamFile(
            @RequestParam String path,
            @AuthenticationPrincipal UserPrincipal user) {
        try {
            String hostRoot = artifactStorageService.getHostRoot();
            String containerRoot = artifactStorageService.getStorageRoot();
            String normalizedPath = path.replace("\\", "/");
            String normalizedHostRoot = hostRoot.replace("\\", "/");
            if (normalizedPath.startsWith(normalizedHostRoot)) {
                normalizedPath = normalizedPath.replace(normalizedHostRoot, containerRoot.replace("\\", "/"));
            }
            
            java.io.File file = new java.io.File(normalizedPath);
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }
            org.springframework.core.io.FileSystemResource resource = new org.springframework.core.io.FileSystemResource(file);
            MediaType mediaType;
            try {
                String mimeType = java.nio.file.Files.probeContentType(file.toPath());
                mediaType = mimeType != null ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM;
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
