package com.karmaos.junction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentKnowledgePackRepository extends JpaRepository<AgentKnowledgePack, AgentKnowledgePackId> {

    List<AgentKnowledgePack> findByIdAgentId(UUID agentId);

    List<AgentKnowledgePack> findByIdKnowledgePackId(UUID knowledgePackId);
}
