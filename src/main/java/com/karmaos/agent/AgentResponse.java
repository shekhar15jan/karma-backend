package com.karmaos.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
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
    private UUID fallbackProviderId;
    private String providerName;
    private String providerType;
    private String model;
    private Double temperature;
    private String memoryMode;
    @JsonProperty("isSystem")
    private boolean isSystem;
    private List<AgentSkillInfo> skills;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentSkillInfo {
        private UUID id;
        private String name;
    }
}
