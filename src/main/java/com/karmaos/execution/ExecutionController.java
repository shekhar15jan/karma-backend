package com.karmaos.execution;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
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

    public ExecutionController(ExecutionService executionService, ExecutionOrchestrator orchestrator) {
        this.executionService = executionService;
        this.orchestrator = orchestrator;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExecutionResponse>>> list(@RequestParam UUID missionId) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.listByMission(missionId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExecutionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExecutionResponse>> create(@Valid @RequestBody ExecutionRequest request) {
        return ResponseEntity.ok(ApiResponse.created(executionService.create(request)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ExecutionResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.start(id)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ExecutionResponse>> complete(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "COMPLETED") String status,
            @RequestParam(required = false) String errorMessage) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.complete(id, status, errorMessage)));
    }

    @GetMapping("/{id}/steps")
    public ResponseEntity<ApiResponse<List<ExecutionStepResponse>>> getSteps(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.getSteps(id)));
    }

    @PostMapping("/trigger/{missionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trigger(@PathVariable UUID missionId) {
        Execution execution = orchestrator.startExecution(missionId);
        orchestrator.executeAsync(execution.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "executionId", execution.getId(),
            "missionId", missionId,
            "status", "PENDING",
            "message", "Execution triggered successfully"
        )));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Map<String, String>>> cancel(@PathVariable UUID id) {
        orchestrator.cancelExecution(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "executionId", id.toString(),
            "status", "CANCELLED"
        )));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@PathVariable UUID id) {
        String status = orchestrator.getExecutionStatus(id);
        boolean active = orchestrator.isActive(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "executionId", id.toString(),
            "status", status,
            "active", active
        )));
    }
}
