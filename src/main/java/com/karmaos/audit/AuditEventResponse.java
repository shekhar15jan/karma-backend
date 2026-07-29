package com.karmaos.audit;

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
public class AuditEventResponse {

    private UUID id;
    private String eventType;
    private String entityType;
    private UUID entityId;
    private UUID actorId;
    private String action;
    private String changes;
    private String metadata;
    private String correlationId;
    private String ipAddress;
    private LocalDateTime createdAt;
}
