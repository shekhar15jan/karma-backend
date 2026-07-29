package com.karmaos.review;

import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactService;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ArtifactService artifactService;

    public ReviewService(ReviewRepository reviewRepository, ArtifactService artifactService) {
        this.reviewRepository = reviewRepository;
        this.artifactService = artifactService;
    }

    public List<ReviewResponse> listByArtifact(Long artifactId) {
        return reviewRepository.findByArtifactIdOrderByCreatedAtDesc(artifactId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReviewResponse create(ReviewRequest request, UserPrincipal user) {
        var artifact = artifactService.getById(request.getArtifactId());
        if (artifact == null) {
            throw ErrorCode.NOT_FOUND.exception("Artifact not found");
        }

        Review review = new Review();
        review.setArtifactId(request.getArtifactId());
        review.setReviewerId(user.getId());
        review.setDecision(request.getDecision());
        review.setComments(request.getComments());
        review.setVersionAtReview(artifact.getCurrentVersion());
        review = reviewRepository.save(review);

        artifactService.updateReviewStatus(request.getArtifactId(), request.getDecision().equals("APPROVED") ? "APPROVED" : "REJECTED");

        return toResponse(review);
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .artifactId(r.getArtifactId())
                .reviewerId(r.getReviewerId())
                .decision(r.getDecision())
                .comments(r.getComments())
                .versionAtReview(r.getVersionAtReview())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
