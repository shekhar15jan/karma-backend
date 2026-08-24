package com.karmaos.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SemanticEmbeddingRepository extends JpaRepository<SemanticEmbedding, UUID> {

    List<SemanticEmbedding> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    @Query(value = """
        SELECT source_type, source_id, content, model, created_at,
               1 - (embedding <=> CAST(:queryVector AS vector)) AS similarity_score
        FROM semantic_embeddings
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> searchSimilar(@Param("queryVector") String queryVector, @Param("topK") int topK);
}
