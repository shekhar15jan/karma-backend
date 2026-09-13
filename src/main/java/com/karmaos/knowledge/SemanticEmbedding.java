package com.karmaos.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "semantic_embeddings")
@Getter
@Setter
@NoArgsConstructor
public class SemanticEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // pgvector: vector(768) when extension is available, fallback to JSONB for plain postgres
    // We keep columnDefinition as vector(768) for pgvector; Flyway V38 migrates JSONB -> vector
    @Column(nullable = false, columnDefinition = "vector(768)")
    private String embedding;

    @Column(length = 64)
    private String model;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
