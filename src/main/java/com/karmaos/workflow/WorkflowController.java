package com.karmaos.workflow;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.WORKFLOW_EXECUTIONS)
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<WorkflowRunResponse>>> runs(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.listRecentRuns(user.getId())));
    }

    @GetMapping("/definitions")
    public ResponseEntity<ApiResponse<List<WorkflowDefinition>>> definitions(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.listAllDefinitions(user.getId())));
    }

    @DeleteMapping("/definitions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDefinition(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        workflowService.deleteDefinition(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/projects/{projectId}/runs")
    public ResponseEntity<ApiResponse<List<WorkflowRunResponse>>> projectRuns(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.listRuns(projectId, user.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkflowDefinition>> saveWorkflow(
            @Valid @RequestBody WorkflowDefinitionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.created(workflowService.createDefinition(request, user.getId())));
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<WorkflowRunResponse>> runWorkflow(
            @RequestBody WorkflowDefinitionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.execute(request, user.getId())));
    }
}
