package com.karmaos.promptversion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, UUID> {

    List<PromptVersion> findByPromptIdOrderByVersionDesc(UUID promptId);

    Optional<PromptVersion> findByPromptIdAndVersion(UUID promptId, int version);

    Optional<PromptVersion> findTopByPromptIdOrderByVersionDesc(UUID promptId);
}
