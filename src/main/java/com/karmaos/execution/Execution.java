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
@Table(name = "executions")
@Getter
@Setter
@NoArgsConstructor
public class Execution extends BaseEntity {

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "total_cost", precision = 10, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String metadata = "{}";
}
