package com.karmaos.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentMcpServerRepository extends JpaRepository<AgentMcpServer, AgentMcpServerId> {
    List<AgentMcpServer> findByIdAgentId(UUID agentId);
    void deleteByIdAgentId(UUID agentId);
}
