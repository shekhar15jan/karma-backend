package com.karmaos.agent;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agent_mcp_servers")
@Getter
@Setter
@NoArgsConstructor
public class AgentMcpServer {
    @EmbeddedId
    private AgentMcpServerId id;
}
