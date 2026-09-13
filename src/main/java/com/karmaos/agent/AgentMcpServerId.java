package com.karmaos.agent;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class AgentMcpServerId implements Serializable {
    private UUID agentId;
    private UUID mcpServerId;
}
