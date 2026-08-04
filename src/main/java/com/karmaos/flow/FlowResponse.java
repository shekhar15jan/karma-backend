package com.karmaos.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowResponse {

    private UUID id;
    private String name;
    private String description;
    private String category;
    private int executionOrder;
    private boolean enabled;
    private String status;
    @JsonProperty("isSystem")
    private boolean isSystem;
    private List<UUID> agentIds;
    private Map<String, Object> design;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
