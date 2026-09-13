package com.karmaos.mcp;

import com.karmaos.mcp.client.McpClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpStartupRunner implements ApplicationRunner {

    private final McpServerRepository mcpServerRepository;
    private final McpClientManager mcpClientManager;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<McpServer> servers = mcpServerRepository.findAllByStatus("ACTIVE");
            if (servers.isEmpty()) {
                log.info("MCP: no ACTIVE servers to start");
                return;
            }
            log.info("MCP: starting {} ACTIVE server(s)", servers.size());
            for (McpServer s : servers) {
                try {
                    mcpClientManager.startServer(s);
                    Thread.sleep(400);
                } catch (Exception e) {
                    log.warn("MCP startup failed for {}: {}", s.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("MCP startup runner failed: {}", e.getMessage());
        }
    }
}
