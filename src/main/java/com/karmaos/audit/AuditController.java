package com.karmaos.audit;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.common.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.AUDIT_LOG)
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AuditEventResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.list(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditEventResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getById(id)));
    }

    @GetMapping("/by-entity")
    public ResponseEntity<ApiResponse<PagedResponse<AuditEventResponse>>> listByEntity(
            @RequestParam String entityType,
            @RequestParam UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.listByEntity(entityType, entityId, page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AuditEventResponse>> create(@Valid @RequestBody AuditEventRequest request) {
        return ResponseEntity.ok(ApiResponse.created(auditService.create(request)));
    }
}
