package com.karmaos.project;

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
public class ProjectRequest {

    @NotNull
    private UUID workspaceId;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 2000)
    private String description;

    private String category;
    private List<String> tags;
    private String brandProfile;
    private UUID defaultProviderId;
}
