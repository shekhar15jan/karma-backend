package com.karmaos.artifact;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.ARTIFACTS)
public class ArtifactController {

    private final ArtifactService artifactService;

    public ArtifactController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArtifactResponse>>> list(@RequestParam Optional<UUID> missionId) {
        if (missionId.isPresent()) {
            return ResponseEntity.ok(ApiResponse.ok(artifactService.listByMission(missionId.get())));
        }
        return ResponseEntity.ok(ApiResponse.ok(artifactService.listAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtifactResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.getById(id)));
    }

    @GetMapping("/pending-review")
    public ResponseEntity<ApiResponse<List<ArtifactResponse>>> pendingReview() {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.listPendingReview()));
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<ApiResponse<ArtifactResponse>> updateReviewStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.updateReviewStatus(id, status)));
    }
}
