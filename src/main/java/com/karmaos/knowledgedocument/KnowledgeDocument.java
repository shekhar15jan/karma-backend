package com.karmaos.knowledgedocument;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "knowledge_documents")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeDocument extends BaseEntity {

    @Column(name = "knowledge_pack_id", nullable = false)
    private UUID knowledgePackId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private long size = 0;

    @Column(nullable = false)
    private boolean indexed = false;

    @Column(length = 64)
    private String checksum;
}
