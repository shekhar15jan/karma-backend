package com.karmaos.workspace;

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
public class WorkspaceResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID ownerId;
    private String status;
    private long projectCount;
    private long missionCount;
    private long artifactCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
