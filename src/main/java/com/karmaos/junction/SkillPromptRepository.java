package com.karmaos.junction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillPromptRepository extends JpaRepository<SkillPrompt, SkillPromptId> {

    List<SkillPrompt> findByIdSkillId(UUID skillId);

    List<SkillPrompt> findByIdPromptId(UUID promptId);
}
