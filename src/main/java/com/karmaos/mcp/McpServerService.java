package com.karmaos.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class McpServerService {

    private final McpServerRepository mcpServerRepository;
    private final ObjectMapper objectMapper;

    public List<McpServerResponse> getAllMcpServers() {
        return mcpServerRepository.findAllByStatus("ACTIVE").stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public McpServerResponse getMcpServer(UUID id) {
        McpServer server = mcpServerRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "MCP Server not found"));
        return toResponse(server);
    }

    @Transactional
    public McpServerResponse createMcpServer(McpServerRequest request) {
        McpServer server = new McpServer();
        server.setName(request.getName());
        server.setTransportType(request.getTransportType() != null ? request.getTransportType() : "stdio");
        server.setCommandOrUrl(request.getCommandOrUrl());
        if (request.getArgs() != null && !request.getArgs().isEmpty()) {
            try {
                server.setArgs(objectMapper.writeValueAsString(request.getArgs()));
            } catch (JsonProcessingException e) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid arguments format");
            }
        }
        if (request.getEnvVars() != null && !request.getEnvVars().isEmpty()) {
            try {
                server.setEnvVars(objectMapper.writeValueAsString(request.getEnvVars()));
            } catch (JsonProcessingException e) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid envVars format");
            }
        }
        server.setCreatedAt(OffsetDateTime.now());
        server.setUpdatedAt(OffsetDateTime.now());
        
        server = mcpServerRepository.save(server);
        return toResponse(server);
    }

    @Transactional
    public McpServerResponse updateMcpServer(UUID id, McpServerRequest request) {
        McpServer server = mcpServerRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "MCP Server not found"));
                
        server.setName(request.getName());
        if (request.getTransportType() != null) {
            server.setTransportType(request.getTransportType());
        }
        server.setCommandOrUrl(request.getCommandOrUrl());
        if (request.getArgs() != null) {
            try {
                server.setArgs(objectMapper.writeValueAsString(request.getArgs()));
            } catch (JsonProcessingException e) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid arguments format");
            }
        } else {
            server.setArgs(null);
        }
        if (request.getEnvVars() != null) {
            try {
                server.setEnvVars(objectMapper.writeValueAsString(request.getEnvVars()));
            } catch (JsonProcessingException e) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid envVars format");
            }
        } else {
            server.setEnvVars(null);
        }
        server.setUpdatedAt(OffsetDateTime.now());
        
        server = mcpServerRepository.save(server);
        return toResponse(server);
    }

    @Transactional
    public void deleteMcpServer(UUID id) {
        McpServer server = mcpServerRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "MCP Server not found"));
        server.setStatus("DELETED");
        server.setUpdatedAt(OffsetDateTime.now());
        mcpServerRepository.save(server);
    }

    private McpServerResponse toResponse(McpServer server) {
        List<String> parsedArgs = null;
        if (server.getArgs() != null) {
            try {
                parsedArgs = objectMapper.readValue(server.getArgs(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                // Ignore
            }
        }
        java.util.Map<String, String> parsedEnvVars = null;
        if (server.getEnvVars() != null) {
            try {
                parsedEnvVars = objectMapper.readValue(server.getEnvVars(), new TypeReference<java.util.Map<String, String>>() {});
            } catch (JsonProcessingException e) {
                // Ignore
            }
        }
        return McpServerResponse.builder()
                .id(server.getId())
                .name(server.getName())
                .transportType(server.getTransportType())
                .commandOrUrl(server.getCommandOrUrl())
                .args(parsedArgs)
                .envVars(parsedEnvVars)
                .status(server.getStatus())
                .createdAt(server.getCreatedAt())
                .updatedAt(server.getUpdatedAt())
                .build();
    }
}
