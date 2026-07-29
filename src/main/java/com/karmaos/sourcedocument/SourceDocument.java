package com.karmaos.sourcedocument;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "source_documents")
@Getter
@Setter
@NoArgsConstructor
public class SourceDocument extends BaseEntity {

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 20)
    private String format = "MARKDOWN";

    @Column(nullable = false)
    private long size = 0;

    @Column(length = 64)
    private String checksum;
}
