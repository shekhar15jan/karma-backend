package com.karmaos.knowledgedocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    List<KnowledgeDocument> findByKnowledgePackId(UUID knowledgePackId);

    long countByKnowledgePackId(UUID knowledgePackId);
}
