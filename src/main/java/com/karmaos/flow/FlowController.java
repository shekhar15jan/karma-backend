package com.karmaos.flow;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.FLOWS)
public class FlowController {

    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FlowResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(flowService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlowResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(flowService.getById(id)));
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<ApiResponse<FlowDetailResponse>> getDetails(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(flowService.getDetails(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FlowResponse>> create(@Valid @RequestBody FlowRequest request) {
        return ResponseEntity.ok(ApiResponse.created(flowService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FlowResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody FlowRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(flowService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        flowService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Flow deleted"));
    }
}
