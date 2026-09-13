package com.karmaos.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/mcp-servers", "/v1/mcp-servers"})
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;
    private final McpToolService mcpToolService;

    @GetMapping
    public ResponseEntity<List<McpServerResponse>> getAllMcpServers() {
        return ResponseEntity.ok(mcpServerService.getAllMcpServers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<McpServerResponse> getMcpServer(@PathVariable UUID id) {
        return ResponseEntity.ok(mcpServerService.getMcpServer(id));
    }

    @PostMapping
    public ResponseEntity<McpServerResponse> createMcpServer(@RequestBody McpServerRequest request) {
        return ResponseEntity.ok(mcpServerService.createMcpServer(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<McpServerResponse> updateMcpServer(@PathVariable UUID id, @RequestBody McpServerRequest request) {
        return ResponseEntity.ok(mcpServerService.updateMcpServer(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMcpServer(@PathVariable UUID id) {
        mcpServerService.deleteMcpServer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/tools")
    public ResponseEntity<JsonNode> listTools(@PathVariable UUID id) {
        return ResponseEntity.ok(mcpToolService.listTools(id));
    }

    @PostMapping("/{id}/tools/call")
    public ResponseEntity<JsonNode> callTool(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String toolName = (String) body.get("name");
        Object arguments = body.get("arguments");
        return ResponseEntity.ok(mcpToolService.callTool(id, toolName, arguments));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> startServer(@PathVariable UUID id) {
        mcpToolService.startServer(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stopServer(@PathVariable UUID id) {
        mcpToolService.stopServer(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(mcpToolService.getServerStatus(id));
    }
}
