package com.karmaos.workflow;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<ApiResponse<List<WorkflowRunResponse>>> runs() {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.listRecentRuns()));
    }

    @GetMapping("/projects/{projectId}/runs")
    public ResponseEntity<ApiResponse<List<WorkflowRunResponse>>> projectRuns(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.listRuns(projectId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkflowDefinition>> saveWorkflow(
            @Valid @RequestBody WorkflowDefinitionRequest request) {
        return ResponseEntity.ok(ApiResponse.created(workflowService.createDefinition(request)));
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<WorkflowRunResponse>> runWorkflow(
            @RequestBody WorkflowDefinitionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        UUID userId = user != null ? user.getId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(ApiResponse.ok(workflowService.execute(request, userId)));
    }
}
