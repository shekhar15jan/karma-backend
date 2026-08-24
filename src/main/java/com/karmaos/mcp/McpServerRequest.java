package com.karmaos.mcp;

import lombok.Data;
import java.util.List;

@Data
public class McpServerRequest {
    private String name;
    private String transportType;
    private String commandOrUrl;
    private List<String> args;
    private java.util.Map<String, String> envVars;
}
