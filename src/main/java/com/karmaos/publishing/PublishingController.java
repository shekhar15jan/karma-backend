package com.karmaos.publishing;

import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.API_V1 + "/publishing")
public class PublishingController {

    private final ArtifactRepository artifactRepository;

    public PublishingController(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    @GetMapping("/channels")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> channels() {
        return ResponseEntity.ok(ApiResponse.ok(List.of()));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> logs() {
        List<Artifact> published = artifactRepository.findByPublicationStatus("PUBLISHED");
        var logs = published.stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId().toString(),
                        "title", a.getName(),
                        "platform", "youtube",
                        "status", "completed",
                        "publishedAt", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null,
                        "views", 0
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }
}
