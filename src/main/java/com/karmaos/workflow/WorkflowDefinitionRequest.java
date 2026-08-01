package com.karmaos.workflow;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionRequest {

    private UUID projectId;

    @NotBlank
    private String name;

    private List<Map<String, Object>> nodes;
    private List<Map<String, String>> edges;
}
