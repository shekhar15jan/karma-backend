package com.karmaos.review;

import com.karmaos.access.OwnershipService;
import com.karmaos.artifact.ArtifactService;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ErrorCode;
import com.karmaos.execution.ExecutionOrchestrator;
import com.karmaos.execution.ExecutionStep;
import com.karmaos.execution.ExecutionStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ArtifactService artifactService;
    private final ExecutionStepRepository stepRepository;
    private final ExecutionOrchestrator orchestrator;
    private final OwnershipService ownershipService;

    public ReviewService(ReviewRepository reviewRepository, ArtifactService artifactService,
                         ExecutionStepRepository stepRepository, ExecutionOrchestrator orchestrator,
                         OwnershipService ownershipService) {
        this.reviewRepository = reviewRepository;
        this.artifactService = artifactService;
        this.stepRepository = stepRepository;
        this.orchestrator = orchestrator;
        this.ownershipService = ownershipService;
    }

    public List<ReviewResponse> listByArtifact(UUID artifactId, UserPrincipal user) {
        ownershipService.getOwnedArtifact(user.getId(), artifactId);
        return reviewRepository.findByArtifactIdOrderByCreatedAtDesc(artifactId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ReviewResponse> listByStep(UUID stepId, UserPrincipal user) {
        ExecutionStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution step not found: " + stepId));
        ownershipService.getOwnedExecution(user.getId(), step.getExecutionId());
        return reviewRepository.findByStepIdOrderByCreatedAtDesc(stepId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReviewResponse create(ReviewRequest request, UserPrincipal user) {
        if (request.getDecision() == null
                || (!"APPROVED".equals(request.getDecision()) && !"REJECTED".equals(request.getDecision()))) {
            throw ErrorCode.BAD_REQUEST.exception("Decision must be APPROVED or REJECTED");
        }
        Review review = new Review();
        review.setReviewerId(user.getId());
        review.setDecision(request.getDecision());
        review.setComments(request.getComments());

        if (request.getStepId() != null) {
            return createStepReview(request, review, user);
        }

        return createArtifactReview(request, review, user);
    }

    private ReviewResponse createStepReview(ReviewRequest request, Review review, UserPrincipal user) {
        ExecutionStep step = stepRepository.findById(request.getStepId())
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Execution step not found: " + request.getStepId()));
        ownershipService.getOwnedExecution(user.getId(), step.getExecutionId());
        if (!"PENDING_REVIEW".equals(step.getReviewStatus())) {
            throw ErrorCode.CONFLICT.exception("Step is not pending review");
        }

        review.setStepId(step.getId());
        review.setVersionAtReview(1);
        review = reviewRepository.save(review);

        if ("APPROVED".equals(request.getDecision())) {
            step.setReviewStatus("APPROVED");
            stepRepository.save(step);
            orchestrator.resumeExecution(step.getExecutionId());
        } else {
            step.setReviewStatus("REJECTED");
            stepRepository.save(step);
            orchestrator.regenerateStep(step.getId(), request.getComments());
        }
        return toResponse(review);
    }

    private ReviewResponse createArtifactReview(ReviewRequest request, Review review, UserPrincipal user) {
        if (request.getArtifactId() == null) {
            throw ErrorCode.BAD_REQUEST.exception("Neither stepId nor artifactId provided");
        }
        var artifact = artifactService.getById(request.getArtifactId(), user);
        if (artifact == null) {
            throw ErrorCode.NOT_FOUND.exception("Artifact not found");
        }

        review.setArtifactId(request.getArtifactId());
        review.setVersionAtReview(artifact.getCurrentVersion());
        review = reviewRepository.save(review);

        artifactService.updateReviewStatus(request.getArtifactId(),
            request.getDecision().equals("APPROVED") ? "APPROVED" : "REJECTED",
            request.getComments(), user);

        return toResponse(review);
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .artifactId(r.getArtifactId())
                .stepId(r.getStepId())
                .reviewerId(r.getReviewerId())
                .decision(r.getDecision())
                .comments(r.getComments())
                .versionAtReview(r.getVersionAtReview())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
