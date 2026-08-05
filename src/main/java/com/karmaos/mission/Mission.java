package com.karmaos.mission;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "missions")
@Getter
@Setter
@NoArgsConstructor
public class Mission extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "mission_type", nullable = false, length = 50)
    private String missionType = "VIDEO";

    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    @Column(length = 20)
    private String priority = "MEDIUM";

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(nullable = false)
    private int progress = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "execution_plan", columnDefinition = "JSONB")
    private String executionPlan;

    @Column(name = "output_directory", length = 500)
    private String outputDirectory;

    @Column(name = "target_duration_seconds")
    private Integer targetDurationSeconds;
}
