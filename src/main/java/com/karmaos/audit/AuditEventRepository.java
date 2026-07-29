package com.karmaos.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    Page<AuditEvent> findByActorId(UUID actorId, Pageable pageable);

    Page<AuditEvent> findByEventType(String eventType, Pageable pageable);
}
