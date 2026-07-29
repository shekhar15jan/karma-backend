package com.karmaos.project;

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
public class ProjectResponse {

    private UUID id;
    private UUID workspaceId;
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private String brandProfile;
    private UUID defaultProviderId;
    private String status;
    private long missionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
