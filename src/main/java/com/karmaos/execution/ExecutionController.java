package com.karmaos.execution;

import com.karmaos.access.OwnershipService;
import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.EXECUTIONS)
public class ExecutionController {

    private final ExecutionService executionService;
    private final ExecutionOrchestrator orchestrator;
    private final OwnershipService ownershipService;

    public ExecutionController(ExecutionService executionService, ExecutionOrchestrator orchestrator,
                               OwnershipService ownershipService) {
        this.executionService = executionService;
        this.orchestrator = orchestrator;
        this.ownershipService = ownershipService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExecutionResponse>>> list(
            @RequestParam(required = false) UUID missionId,
            @AuthenticationPrincipal UserPrincipal user) {
        if (missionId != null) {
            return ResponseEntity.ok(ApiResponse.ok(executionService.listByMission(missionId, user)));
        }
        return ResponseEntity.ok(ApiResponse.ok(executionService.listOwned(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExecutionResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.getById(id, user)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExecutionResponse>> create(
            @Valid @RequestBody ExecutionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        ownershipService.getOwnedMission(user.getId(), request.getMissionId());
        return ResponseEntity.ok(ApiResponse.created(executionService.create(request)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ExecutionResponse>> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        ownershipService.getOwnedExecution(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(executionService.start(id)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ExecutionResponse>> complete(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "COMPLETED") String status,
            @RequestParam(required = false) String errorMessage,
            @AuthenticationPrincipal UserPrincipal user) {
        ownershipService.getOwnedExecution(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(executionService.complete(id, status, errorMessage)));
    }

    @GetMapping("/{id}/steps")
    public ResponseEntity<ApiResponse<List<ExecutionStepResponse>>> getSteps(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.getSteps(id, user)));
    }

    @GetMapping("/pending-step-reviews")
    public ResponseEntity<ApiResponse<List<PendingStepReviewResponse>>> pendingStepReviews(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.listPendingStepReviews(user.getId())));
    }

    @PostMapping("/trigger/{missionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trigger(
            @PathVariable UUID missionId,
            @RequestParam(defaultValue = "REVIEW") String mode,
            @AuthenticationPrincipal UserPrincipal user) {
        ownershipService.getOwnedMission(user.getId(), missionId);
        Execution execution = orchestrator.startExecutionAfterCommit(missionId, mode);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "executionId", execution.getId(),
            "missionId", missionId,
            "mode", execution.getMode(),
            "status", "PENDING",
            "message", "Execution triggered successfully"
        )));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Map<String, String>>> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        ownershipService.getOwnedExecution(user.getId(), id);
        orchestrator.cancelExecution(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "executionId", id.toString(),
            "status", "CANCELLED"
        )));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        ownershipService.getOwnedExecution(user.getId(), id);
        String status = orchestrator.getExecutionStatus(id);
        boolean active = orchestrator.isActive(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "executionId", id.toString(),
            "status", status,
            "active", active
        )));
    }
}
