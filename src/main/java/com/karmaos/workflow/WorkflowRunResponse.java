package com.karmaos.workflow;

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
public class WorkflowRunResponse {

    private UUID id;
    private UUID workflowId;
    private UUID projectId;
    private String workflowName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer duration;
    private LocalDateTime createdAt;
}
