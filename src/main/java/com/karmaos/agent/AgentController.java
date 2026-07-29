package com.karmaos.agent;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.AGENTS)
public class AgentController {

    private final AgentService agentService;
    private final AgentRuntimeService agentRuntimeService;

    public AgentController(AgentService agentService, AgentRuntimeService agentRuntimeService) {
        this.agentService = agentService;
        this.agentRuntimeService = agentRuntimeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(agentService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.getById(id)));
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeAgentPrompt(@RequestBody Map<String, String> payload) {
        String systemPrompt = payload.get("systemPrompt");
        String userMessage = payload.get("userMessage");
        Map<String, Object> result = agentRuntimeService.executePromptSync(systemPrompt, userMessage);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AgentResponse>> create(@Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(ApiResponse.created(agentService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        agentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Agent deleted"));
    }
}
