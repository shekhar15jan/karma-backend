package com.karmaos.audit;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventRequest {

    @NotBlank
    private String eventType;

    private String entityType;
    private UUID entityId;
    private UUID actorId;

    @NotBlank
    private String action;

    private String changes;
    private String metadata;
    private String correlationId;
    private String ipAddress;
}
