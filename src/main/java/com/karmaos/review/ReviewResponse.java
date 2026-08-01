package com.karmaos.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private UUID id;
    private UUID artifactId;
    private UUID stepId;
    private UUID reviewerId;
    private String decision;
    private String comments;
    private int versionAtReview;
    private LocalDateTime createdAt;
}
