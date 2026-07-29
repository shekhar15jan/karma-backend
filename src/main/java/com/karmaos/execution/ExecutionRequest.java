package com.karmaos.execution;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRequest {

    @NotNull
    private UUID missionId;

    private UUID providerId;
    private String modelUsed;
}
