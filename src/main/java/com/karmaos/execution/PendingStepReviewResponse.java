package com.karmaos.execution;

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
public class PendingStepReviewResponse {

    private UUID stepId;
    private UUID executionId;
    private UUID missionId;
    private String missionName;
    private UUID flowId;
    private String flowName;
    private UUID agentId;
    private String agentName;
    private int stepOrder;
    private String stepType;
    private String status;
    private String reviewStatus;
    private String inputData;
    private String outputData;
    private int retryCount;
    private int maxRetries;
    private LocalDateTime createdAt;
}
