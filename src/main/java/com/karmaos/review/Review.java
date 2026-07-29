package com.karmaos.review;

import com.karmaos.common.BaseEntityLongId;
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
public class Review extends BaseEntityLongId {

    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(nullable = false, length = 50)
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "version_at_review", nullable = false)
    private int versionAtReview;
}
