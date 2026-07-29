package com.karmaos.agent;

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
public class AgentResponse {

    private UUID id;
    private String name;
    private String description;
    private String category;
    private String icon;
    private String status;
    private UUID defaultPromptId;
    private UUID defaultProviderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
