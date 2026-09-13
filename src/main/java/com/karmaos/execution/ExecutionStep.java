package com.karmaos.execution;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "execution_steps")
@Getter
@Setter
@NoArgsConstructor
public class ExecutionStep extends BaseEntity {

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "flow_id")
    private UUID flowId;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder = 0;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "step_type", nullable = false, length = 50)
    private String stepType;

    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus = "NONE";

    @Column(name = "input_data", columnDefinition = "TEXT")
    private String inputData;

    @Column(name = "output_data", columnDefinition = "TEXT")
    private String outputData;

    @Column(name = "prompt_used", columnDefinition = "TEXT")
    private String promptUsed;

    @Column(name = "prompt_version")
    private Integer promptVersion;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "prompt_tokens")
    private int promptTokens = 0;

    @Column(name = "completion_tokens")
    private int completionTokens = 0;

    @Column(name = "total_tokens")
    private int totalTokens = 0;

    @Column(precision = 10, scale = 4)
    private BigDecimal cost;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
