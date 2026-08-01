package com.karmaos.agent;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.execution.ExecutionService;
import com.karmaos.execution.ExecutionStepResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.AGENTS)
public class AgentController {

    private final AgentService agentService;
    private final AgentRuntimeService agentRuntimeService;
    private final ExecutionService executionService;

    public AgentController(AgentService agentService, AgentRuntimeService agentRuntimeService, ExecutionService executionService) {
        this.agentService = agentService;
        this.agentRuntimeService = agentRuntimeService;
        this.executionService = executionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(agentService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.getById(id)));
    }

    @GetMapping("/{id}/trace")
    public ResponseEntity<ApiResponse<List<ExecutionStepResponse>>> trace(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(executionService.listStepsByAgent(id, user)));
    }

    @PostMapping("/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeAgentPrompt(@RequestBody Map<String, String> payload) {
        String systemPrompt = payload.get("systemPrompt");
        String userMessage = payload.get("userMessage");
        Map<String, Object> result = agentRuntimeService.executePromptSync(systemPrompt, userMessage);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> create(@Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(ApiResponse.created(agentService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        agentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Agent deleted"));
    }
}
