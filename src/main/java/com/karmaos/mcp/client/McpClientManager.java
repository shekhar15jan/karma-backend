package com.karmaos.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.karmaos.mcp.McpServer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientManager {

    private final ObjectMapper objectMapper;
    private final Map<UUID, McpProcess> activeProcesses = new ConcurrentHashMap<>();
    private final Map<UUID, SseMcpConnection> sseConnections = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isRunning(UUID serverId) {
        return activeProcesses.containsKey(serverId) || sseConnections.containsKey(serverId);
    }

    public void startServer(McpServer server) {
        String transport = server.getTransportType() != null ? server.getTransportType().toLowerCase() : "stdio";
        if ("sse".equals(transport)) {
            startSseServer(server);
        } else {
            startStdioServer(server);
        }
    }

    private void startStdioServer(McpServer server) {
        try {
            List<String> command = new ArrayList<>();
            command.add(server.getCommandOrUrl());
            if (server.getArgs() != null) {
                List<String> parsedArgs = objectMapper.readValue(server.getArgs(), new TypeReference<List<String>>() {});
                command.addAll(parsedArgs);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            
            if (server.getEnvVars() != null) {
                Map<String, String> envVars = objectMapper.readValue(server.getEnvVars(), new TypeReference<Map<String, String>>() {});
                pb.environment().putAll(envVars);
            }

            Process process = pb.start();
            McpProcess mcpProcess = new McpProcess(process, objectMapper);
            activeProcesses.put(server.getId(), mcpProcess);
            
            executor.submit(() -> mcpProcess.readLoop());
            // pipe stderr to log
            executor.submit(() -> {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = err.readLine()) != null) {
                        log.info("[MCP:{} stderr] {}", server.getName(), line);
                    }
                } catch (IOException e) {
                    log.debug("MCP stderr closed for {}", server.getName());
                }
            });
            
            log.info("Started MCP stdio server: {} ({})", server.getName(), server.getId());
            mcpProcess.sendInitialize().orTimeout(15, TimeUnit.SECONDS).exceptionally(ex -> {
                log.warn("MCP initialize timeout/error for {}: {}", server.getName(), ex.getMessage());
                return null;
            });
            
        } catch (Exception e) {
            log.error("Failed to start MCP stdio server: {}", server.getName(), e);
        }
    }

    private void startSseServer(McpServer server) {
        try {
            String url = server.getCommandOrUrl();
            SseMcpConnection conn = new SseMcpConnection(server.getId(), url, objectMapper, httpClient);
            sseConnections.put(server.getId(), conn);
            executor.submit(conn::connectAndListen);
            log.info("Started MCP SSE server: {} -> {}", server.getName(), url);
        } catch (Exception e) {
            log.error("Failed to start MCP SSE server: {}", server.getName(), e);
        }
    }

    public void stopServer(UUID serverId) {
        McpProcess process = activeProcesses.remove(serverId);
        if (process != null) {
            process.stop();
            log.info("Stopped MCP stdio server {}", serverId);
        }
        SseMcpConnection sse = sseConnections.remove(serverId);
        if (sse != null) {
            sse.stop();
            log.info("Stopped MCP SSE server {}", serverId);
        }
    }

    public JsonNode fetchTools(UUID serverId) throws Exception {
        String transport = getTransport(serverId);
        if ("sse".equals(transport)) {
            SseMcpConnection sse = sseConnections.get(serverId);
            if (sse == null) throw new IllegalStateException("SSE server not running: " + serverId);
            return sse.sendRequest("tools/list", objectMapper.createObjectNode()).get(20, TimeUnit.SECONDS);
        }
        McpProcess process = activeProcesses.get(serverId);
        if (process == null) throw new IllegalStateException("Server not running: " + serverId);
        return process.sendRequest("tools/list", objectMapper.createObjectNode()).get(20, TimeUnit.SECONDS);
    }
    
    public JsonNode callTool(UUID serverId, String name, JsonNode arguments) throws Exception {
        var params = objectMapper.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments != null ? arguments : objectMapper.createObjectNode());
        
        String transport = getTransport(serverId);
        if ("sse".equals(transport)) {
            SseMcpConnection sse = sseConnections.get(serverId);
            if (sse == null) throw new IllegalStateException("SSE server not running: " + serverId);
            return sse.sendRequest("tools/call", params).get(30, TimeUnit.SECONDS);
        }
        McpProcess process = activeProcesses.get(serverId);
        if (process == null) throw new IllegalStateException("Server not running: " + serverId);
        return process.sendRequest("tools/call", params).get(30, TimeUnit.SECONDS);
    }

    private String getTransport(UUID serverId) {
        if (sseConnections.containsKey(serverId)) return "sse";
        return "stdio";
    }

    public void restartServer(McpServer server) {
        stopServer(server.getId());
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        startServer(server);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MCP client manager ({} stdio, {} sse)", activeProcesses.size(), sseConnections.size());
        for (UUID id : new ArrayList<>(activeProcesses.keySet())) stopServer(id);
        for (UUID id : new ArrayList<>(sseConnections.keySet())) stopServer(id);
        executor.shutdownNow();
    }

    // ---- STDIO process ----
    private static class McpProcess {
        private final Process process;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private final ObjectMapper objectMapper;
        private final AtomicInteger messageId = new AtomicInteger(1);
        private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

        public McpProcess(Process process, ObjectMapper objectMapper) {
            this.process = process;
            this.objectMapper = objectMapper;
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        }

        public CompletableFuture<JsonNode> sendInitialize() throws Exception {
            var params = objectMapper.createObjectNode();
            params.put("protocolVersion", "2024-11-05");
            var clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "KarmaOS");
            clientInfo.put("version", "1.0.0");
            params.putObject("capabilities");
            
            CompletableFuture<JsonNode> init = sendRequest("initialize", params);
            init.thenAccept(result -> {
                try {
                    var notification = objectMapper.createObjectNode();
                    notification.put("jsonrpc", "2.0");
                    notification.put("method", "notifications/initialized");
                    sendRawMessage(objectMapper.writeValueAsString(notification));
                } catch (Exception e) {
                    log.error("Failed to send initialized notification", e);
                }
            });
            return init;
        }

        public CompletableFuture<JsonNode> sendRequest(String method, JsonNode params) throws Exception {
            int id = messageId.getAndIncrement();
            var request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            if (params != null) {
                request.set("params", params);
            }
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingRequests.put(id, future);
            sendRawMessage(objectMapper.writeValueAsString(request));
            return future;
        }

        private synchronized void sendRawMessage(String content) throws IOException {
            byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String message = "Content-Length: " + bytes.length + "\r\n\r\n" + content;
            writer.write(message);
            writer.flush();
        }

        public void readLoop() {
            try {
                while (process.isAlive()) {
                    String line = reader.readLine();
                    if (line == null) break;
                    if (line.startsWith("Content-Length: ")) {
                        int length = Integer.parseInt(line.substring(16).trim());
                        reader.readLine(); // skip empty line
                        char[] buffer = new char[length];
                        int read = 0;
                        while (read < length) {
                            int r = reader.read(buffer, read, length - read);
                            if (r == -1) break;
                            read += r;
                        }
                        if (read == 0) continue;
                        String content = new String(buffer, 0, read);
                        JsonNode node;
                        try {
                            node = objectMapper.readTree(content);
                        } catch (Exception e) {
                            log.warn("MCP invalid JSON: {}", content.substring(0, Math.min(200, content.length())));
                            continue;
                        }
                        if (node.has("id")) {
                            int id = node.get("id").asInt();
                            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                            if (future != null) {
                                if (node.has("error")) {
                                    future.completeExceptionally(new RuntimeException(node.get("error").toString()));
                                } else {
                                    future.complete(node.has("result") ? node.get("result") : node);
                                }
                            }
                        } else if (node.has("method")) {
                            log.debug("MCP notification: {}", node.get("method").asText());
                        }
                    } else if (!line.isBlank()) {
                        log.debug("MCP stdout: {}", line);
                    }
                }
            } catch (Exception e) {
                if (process.isAlive()) log.error("Error reading from MCP process", e);
            } finally {
                for (CompletableFuture<JsonNode> f : pendingRequests.values()) {
                    f.completeExceptionally(new RuntimeException("MCP process closed"));
                }
                pendingRequests.clear();
            }
        }

        public void stop() {
            try { reader.close(); } catch (Exception ignored) {}
            try { writer.close(); } catch (Exception ignored) {}
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
        }
    }

    // ---- SSE connection (JSON-RPC over HTTP) ----
    private static class SseMcpConnection {
        private final UUID serverId;
        private final String baseUrl;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;
        private final AtomicInteger messageId = new AtomicInteger(1);
        private volatile boolean running = true;

        SseMcpConnection(UUID serverId, String baseUrl, ObjectMapper objectMapper, HttpClient httpClient) {
            this.serverId = serverId;
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
            this.objectMapper = objectMapper;
            this.httpClient = httpClient;
        }

        void connectAndListen() {
            // SSE servers use HTTP JSON-RPC; we just keep connection lazy — requests are plain POSTs.
            log.info("MCP SSE ready for {}", serverId);
        }

        CompletableFuture<JsonNode> sendRequest(String method, JsonNode params) throws Exception {
            int id = messageId.getAndIncrement();
            var request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            if (params != null) request.set("params", params);
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            httpClient.sendAsync(httpReq, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() >= 400) throw new RuntimeException("SSE MCP HTTP " + resp.statusCode() + ": " + resp.body());
                        try { return objectMapper.readTree(resp.body()); } catch (Exception e) { throw new RuntimeException(e); }
                    })
                    .thenAccept(node -> {
                        if (node.has("error")) future.completeExceptionally(new RuntimeException(node.get("error").toString()));
                        else future.complete(node.has("result") ? node.get("result") : node);
                    })
                    .exceptionally(ex -> { future.completeExceptionally(ex); return null; });
            return future;
        }

        void stop() { running = false; }
    }
}
