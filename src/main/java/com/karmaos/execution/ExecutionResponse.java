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
public class ExecutionResponse {

    private UUID id;
    private UUID missionId;
    private String mode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private UUID providerId;
    private String modelUsed;
    private BigDecimal totalCost;
    private String errorMessage;
    private LocalDateTime createdAt;
}
