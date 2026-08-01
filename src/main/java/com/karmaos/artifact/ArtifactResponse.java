package com.karmaos.artifact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactResponse {

    private UUID id;
    private UUID missionId;
    private UUID executionStepId;
    private String name;
    private String artifactType;
    private String status;
    private String contentText;
    private String contentType;
    private String reviewStatus;
    private String publicationStatus;
    private List<String> tags;
    private int currentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
