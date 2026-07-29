package com.karmaos.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 2000)
    private String description;
}
