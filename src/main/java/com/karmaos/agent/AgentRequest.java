package com.karmaos.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 2000)
    private String description;

    private String category;
    private String icon;
    private UUID defaultPromptId;
    private UUID defaultProviderId;
    private UUID fallbackProviderId;
    private Object configuration;
    private String model;
    private Double temperature;
    private String memoryMode;
    private List<UUID> skillIds;
    private List<UUID> mcpServerIds;
}
