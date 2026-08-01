package com.karmaos.review;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.REVIEWS)
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/artifact/{artifactId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> list(
            @PathVariable UUID artifactId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listByArtifact(artifactId, user)));
    }

    @GetMapping("/step/{stepId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> listByStep(
            @PathVariable UUID stepId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listByStep(stepId, user)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.created(reviewService.create(request, user)));
    }
}
