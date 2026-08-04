package com.karmaos.flow;

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
public class FlowRunSummary {

    private UUID id;
    private UUID executionId;
    private UUID missionId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer duration;
}