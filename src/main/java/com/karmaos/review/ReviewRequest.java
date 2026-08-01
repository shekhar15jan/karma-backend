package com.karmaos.review;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    private UUID artifactId;

    private UUID stepId;

    @NotBlank
    private String decision;

    private String comments;

    @AssertTrue(message = "Either artifactId or stepId must be provided")
    public boolean isTargetProvided() {
        return artifactId != null || stepId != null;
    }
}
