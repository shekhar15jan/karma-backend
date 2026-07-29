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

    private Long id;
    private Long artifactId;
    private UUID reviewerId;
    private String decision;
    private String comments;
    private int versionAtReview;
    private LocalDateTime createdAt;
}
