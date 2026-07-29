package com.karmaos.mission;

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
public class MissionResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private String missionType;
    private String status;
    private String priority;
    private UUID providerId;
    private int progress;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
