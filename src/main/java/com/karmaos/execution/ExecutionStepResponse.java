package com.karmaos.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStepResponse {

    private UUID id;
    private UUID executionId;
    private UUID flowId;
    private UUID agentId;
    private int stepOrder;
    private String stepType;
    private String status;
    private String inputData;
    private String outputData;
    private String promptUsed;
    private Integer promptVersion;
    private UUID providerId;
    private String modelUsed;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private BigDecimal cost;
    private Integer latencyMs;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
