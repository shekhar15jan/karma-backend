package com.karmaos.mcp;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class McpServerResponse {
    private UUID id;
    private String name;
    private String transportType;
    private String commandOrUrl;
    private List<String> args;
    private java.util.Map<String, String> envVars;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
