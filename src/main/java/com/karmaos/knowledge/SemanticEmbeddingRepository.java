package com.karmaos.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SemanticEmbeddingRepository extends JpaRepository<SemanticEmbedding, UUID> {

    List<SemanticEmbedding> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);
}
