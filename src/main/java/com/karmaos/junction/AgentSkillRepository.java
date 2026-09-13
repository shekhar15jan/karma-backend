package com.karmaos.junction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentSkillRepository extends JpaRepository<AgentSkill, AgentSkillId> {

    List<AgentSkill> findByIdAgentId(UUID agentId);

    List<AgentSkill> findByIdSkillId(UUID skillId);

    void deleteAllByIdSkillId(UUID skillId);
}
