package com.karmaos.audit;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "JSONB")
    private String changes;

    @Column(columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
