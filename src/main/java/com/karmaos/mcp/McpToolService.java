package com.karmaos.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karmaos.agent.AgentMcpServerRepository;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.mcp.client.McpClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private final McpServerRepository mcpServerRepository;
    private final AgentMcpServerRepository agentMcpServerRepository;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper;

    // --- REST helpers ---

    public JsonNode listTools(UUID serverId) {
        McpServer server = mcpServerRepository.findByIdAndStatus(serverId, "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "MCP Server not found"));
        try {
            if (!mcpClientManager.isRunning(serverId)) {
                mcpClientManager.startServer(server);
                Thread.sleep(800);
            }
            return mcpClientManager.fetchTools(serverId);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Failed to list tools for " + server.getName() + ": " + e.getMessage());
        }
    }

    public JsonNode callTool(UUID serverId, String toolName, Object arguments) {
        McpServer server = mcpServerRepository.findByIdAndStatus(serverId, "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "MCP Server not found"));
        if (toolName == null || toolName.isBlank()) throw new ApiException(ErrorCode.BAD_REQUEST, "tool name required");
        try {
            if (!mcpClientManager.isRunning(serverId)) {
                mcpClientManager.startServer(server);
                Thread.sleep(800);
            }
            JsonNode argsNode = arguments == null ? objectMapper.createObjectNode()
                    : objectMapper.valueToTree(arguments);
            // unwrap if wrapped as {arguments: {...}}
            if (argsNode.has("arguments")) argsNode = argsNode.get("arguments");
            return mcpClientManager.callTool(serverId, toolName, argsNode);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Tool call failed: " + e.getMessage());
        }
    }

    public void startServer(UUID serverId) {
        McpServer server = mcpServerRepository.findByIdAndStatus(serverId, "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "MCP Server not found"));
        mcpClientManager.startServer(server);
    }

    public void stopServer(UUID serverId) { mcpClientManager.stopServer(serverId); }

    public Map<String, Object> getServerStatus(UUID serverId) {
        McpServer server = mcpServerRepository.findByIdAndStatus(serverId, "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "MCP Server not found"));
        boolean running = mcpClientManager.isRunning(serverId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", server.getId());
        m.put("name", server.getName());
        m.put("transportType", server.getTransportType());
        m.put("running", running);
        m.put("status", server.getStatus());
        return m;
    }

    // --- Agent integration ---

    /**
     * Returns all ACTIVE MCP servers bound to the agent, started if needed, with their tool catalog.
     * Used to inject tool definitions into the LLM prompt.
     */
    public List<McpServerTools> getToolsForAgent(UUID agentId) {
        if (agentId == null) return List.of();
        List<UUID> serverIds = agentMcpServerRepository.findByIdAgentId(agentId).stream()
                .map(am -> am.getId().getMcpServerId())
                .toList();
        if (serverIds.isEmpty()) return List.of();
        List<McpServerTools> out = new ArrayList<>();
        for (UUID sid : serverIds) {
            Optional<McpServer> opt = mcpServerRepository.findByIdAndStatus(sid, "ACTIVE");
            if (opt.isEmpty()) continue;
            McpServer srv = opt.get();
            try {
                if (!mcpClientManager.isRunning(sid)) {
                    mcpClientManager.startServer(srv);
                    Thread.sleep(600);
                }
                JsonNode toolsResult = mcpClientManager.fetchTools(sid);
                List<JsonNode> tools = extractTools(toolsResult);
                out.add(new McpServerTools(srv.getId(), srv.getName(), srv.getTransportType(), tools));
            } catch (Exception e) {
                log.warn("MCP tool discovery failed for server {} ({}): {}", srv.getName(), sid, e.getMessage());
                out.add(new McpServerTools(srv.getId(), srv.getName(), srv.getTransportType(), List.of()));
            }
        }
        return out;
    }

    /**
     * Formats tool catalog as a compact LLM prompt block. Each tool shows name, description, inputSchema.
     */
    public String buildToolPromptBlock(List<McpServerTools> catalog) {
        if (catalog == null || catalog.isEmpty()) return "";
        int total = catalog.stream().mapToInt(c -> c.tools().size()).sum();
        if (total == 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[MCP TOOLS AVAILABLE — you MAY call them via JSON]\n");
        sb.append("You have access to external tools through MCP servers. To call a tool, output a single JSON object with the shape:\n");
        sb.append("{\"tool_call\": {\"serverId\": \"<uuid>\", \"serverName\": \"<name>\", \"tool\": \"<toolName>\", \"arguments\": { ... }}}\n");
        sb.append("The system will execute the tool and return the result; then you continue. You may chain up to 5 tool calls.\n");
        sb.append("Available tools:\n");
        for (McpServerTools entry : catalog) {
            sb.append("- Server: ").append(entry.serverName()).append(" (").append(entry.serverId()).append(") [").append(entry.transportType()).append("]\n");
            if (entry.tools().isEmpty()) {
                sb.append("  (no tools discovered — server may be starting)\n");
            }
            for (JsonNode tool : entry.tools()) {
                String name = tool.path("name").asText("unknown");
                String desc = tool.path("description").asText("");
                sb.append("  * ").append(name);
                if (!desc.isBlank()) sb.append(" — ").append(desc.length() > 200 ? desc.substring(0, 200) : desc);
                sb.append("\n");
                JsonNode schema = tool.path("inputSchema");
                if (schema.isObject() && schema.has("properties")) {
                    try {
                        String s = objectMapper.writeValueAsString(schema.get("properties"));
                        if (s.length() > 400) s = s.substring(0, 400) + "...";
                        sb.append("    inputSchema: ").append(s).append("\n");
                    } catch (Exception ignored) {}
                }
            }
        }
        return sb.toString();
    }

    /**
     * Tries to parse a tool_call JSON from LLM output. Returns null if none.
     * Accepts both {"tool_call": {...}} and nested in prose via StrictJson extraction.
     */
    public ToolCall parseToolCall(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return null;
        try {
            // fast path: direct JSON object
            JsonNode root = objectMapper.readTree(llmOutput.trim());
            if (root.has("tool_call")) return toToolCall(root.get("tool_call"));
        } catch (Exception ignored) {}
        try {
            // fallback: extract last JSON object that contains tool_call
            // simple scan for "tool_call" then try to parse surrounding braces
            int idx = llmOutput.lastIndexOf("\"tool_call\"");
            if (idx < 0) idx = llmOutput.lastIndexOf("'tool_call'");
            if (idx >= 0) {
                // find opening brace before idx
                int start = llmOutput.lastIndexOf('{', idx);
                if (start >= 0) {
                    // find matching close by counting braces
                    int depth = 0;
                    for (int i = start; i < llmOutput.length(); i++) {
                        char c = llmOutput.charAt(i);
                        if (c == '{') depth++;
                        else if (c == '}') { depth--; if (depth == 0) { String candidate = llmOutput.substring(start, i+1); try { JsonNode n = objectMapper.readTree(candidate); if (n.has("tool_call")) return toToolCall(n.get("tool_call")); } catch (Exception e2) { break; } } }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private ToolCall toToolCall(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        String tool = node.path("tool").asText(node.path("name").asText(""));
        if (tool.isBlank()) return null;
        String serverId = node.path("serverId").asText(node.path("server_id").asText(""));
        String serverName = node.path("serverName").asText(node.path("server_name").asText(""));
        JsonNode args = node.has("arguments") ? node.get("arguments") : objectMapper.createObjectNode();
        if (args.isNull() || !args.isObject()) args = objectMapper.createObjectNode();
        UUID sid = null;
        if (!serverId.isBlank()) try { sid = UUID.fromString(serverId); } catch (Exception ignored) {}
        return new ToolCall(sid, serverName, tool, (ObjectNode) args);
    }

    public JsonNode executeToolCall(ToolCall call, List<McpServerTools> catalog) throws Exception {
        UUID target = call.serverId();
        if (target == null && call.serverName() != null && !call.serverName().isBlank()) {
            for (McpServerTools e : catalog) {
                if (e.serverName().equalsIgnoreCase(call.serverName())) { target = e.serverId(); break; }
                // also try partial
                if (e.serverName().toLowerCase().contains(call.serverName().toLowerCase())) { target = e.serverId(); break; }
            }
        }
        if (target == null && catalog.size() == 1) target = catalog.get(0).serverId();
        if (target == null) throw new IllegalArgumentException("Cannot resolve MCP server for tool call: " + call.tool());
        return mcpClientManager.callTool(target, call.tool(), call.arguments());
    }

    private List<JsonNode> extractTools(JsonNode result) {
        if (result == null) return List.of();
        // MCP tools/list returns {tools: [{name, description, inputSchema}]}
        if (result.has("tools") && result.get("tools").isArray()) {
            List<JsonNode> out = new ArrayList<>();
            for (JsonNode t : result.get("tools")) out.add(t);
            return out;
        }
        if (result.isArray()) {
            List<JsonNode> out = new ArrayList<>();
            for (JsonNode t : result) out.add(t);
            return out;
        }
        return List.of();
    }

    public record McpServerTools(UUID serverId, String serverName, String transportType, List<JsonNode> tools) {}
    public record ToolCall(UUID serverId, String serverName, String tool, ObjectNode arguments) {}
}
