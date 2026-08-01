package com.karmaos.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgePackRepository extends JpaRepository<KnowledgePack, UUID> {

    List<KnowledgePack> findByWorkspaceIdAndStatusNot(UUID workspaceId, String status);

    List<KnowledgePack> findByWorkspaceIdInAndStatusNot(Collection<UUID> workspaceIds, String status);

    Optional<KnowledgePack> findByIdAndStatusNot(UUID id, String status);

    List<KnowledgePack> findByNameContainingIgnoreCase(String name);
}
