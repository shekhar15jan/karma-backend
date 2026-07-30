package com.karmaos.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_runs")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID DEFAULT gen_random_uuid()")
    private UUID id;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "mission_id")
    private UUID missionId;

    @Column(name = "flow_id")
    private UUID flowId;

    @Column(name = "workflow_name", nullable = false, length = 255)
    private String workflowName;

    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column
    private Integer duration;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
