package com.karmaos.artifact;

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
public class ArtifactRequest {

    private UUID missionId;

    private UUID executionStepId;

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 50)
    private String artifactType;

    private String contentText;

    @Size(max = 100)
    private String contentType;

    private List<String> tags;
}
