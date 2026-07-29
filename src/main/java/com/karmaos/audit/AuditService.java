package com.karmaos.audit;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.common.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public PagedResponse<AuditEventResponse> list(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditEvent> result = repository.findAll(pageRequest);
        return PagedResponse.of(
                result.getContent().stream().map(this::toResponse).collect(Collectors.toList()),
                page, size, result.getTotalElements());
    }

    public PagedResponse<AuditEventResponse> listByEntity(String entityType, UUID entityId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditEvent> result = repository.findByEntityTypeAndEntityId(entityType, entityId, pageRequest);
        return PagedResponse.of(
                result.getContent().stream().map(this::toResponse).collect(Collectors.toList()),
                page, size, result.getTotalElements());
    }

    public AuditEventResponse getById(UUID id) {
        AuditEvent event = repository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Audit event not found: " + id));
        return toResponse(event);
    }

    @Transactional
    public AuditEventResponse create(AuditEventRequest request) {
        AuditEvent event = new AuditEvent();
        event.setEventType(request.getEventType());
        event.setEntityType(request.getEntityType());
        event.setEntityId(request.getEntityId());
        event.setActorId(request.getActorId());
        event.setAction(request.getAction());
        event.setChanges(request.getChanges());
        event.setMetadata(request.getMetadata());
        event.setCorrelationId(request.getCorrelationId());
        event.setIpAddress(request.getIpAddress());
        event = repository.save(event);
        return toResponse(event);
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        return AuditEventResponse.builder()
                .id(e.getId())
                .eventType(e.getEventType())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .actorId(e.getActorId())
                .action(e.getAction())
                .changes(e.getChanges())
                .metadata(e.getMetadata())
                .correlationId(e.getCorrelationId())
                .ipAddress(e.getIpAddress())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
