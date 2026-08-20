package com.karmaos.mission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class MissionRequest {

    @NotNull
    private UUID projectId;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 5000)
    private String description;

    private String missionType;
    private String priority;
    private UUID providerId;
    private List<UUID> selectedFlowIds;

    @Size(max = 500)
    private String outputDirectory;

    private Integer targetDurationSeconds;

    @Size(max = 50)
    private String theme;
}
