package com.karmaos.mcp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "mcp_servers")
public class McpServer {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "transport_type", nullable = false)
    private String transportType = "stdio"; // 'stdio' or 'sse'

    @Column(name = "command_or_url", nullable = false, length = 1024)
    private String commandOrUrl;

    @Column
    private String args; // JSON array of strings

    @Column(name = "env_vars")
    private String envVars; // JSON object of env variables

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
