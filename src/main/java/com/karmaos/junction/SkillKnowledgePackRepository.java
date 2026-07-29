package com.karmaos.junction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillKnowledgePackRepository extends JpaRepository<SkillKnowledgePack, SkillKnowledgePackId> {

    List<SkillKnowledgePack> findByIdSkillId(UUID skillId);

    List<SkillKnowledgePack> findByIdKnowledgePackId(UUID knowledgePackId);
}
