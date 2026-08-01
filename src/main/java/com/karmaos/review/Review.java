package com.karmaos.review;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review extends BaseEntity {

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "step_id")
    private UUID stepId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(nullable = false, length = 50)
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "version_at_review", nullable = false)
    private int versionAtReview;
}
