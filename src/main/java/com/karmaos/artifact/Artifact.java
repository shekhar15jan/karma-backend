package com.karmaos.artifact;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "artifacts")
@Getter
@Setter
@NoArgsConstructor
public class Artifact extends BaseEntity {

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    @Column(name = "execution_step_id")
    private UUID executionStepId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "artifact_type", nullable = false, length = 50)
    private String artifactType;

    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    @Column(name = "storage_location", columnDefinition = "TEXT")
    private String storageLocation;

    @Column(length = 64)
    private String checksum;

    @Column(nullable = false)
    private long size = 0;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "content_type", length = 100)
    private String contentType = "text/markdown";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String metadata = "{}";

    @Column(name = "review_status", nullable = false, length = 50)
    private String reviewStatus = "PENDING";

    @Column(name = "publication_status", nullable = false, length = 50)
    private String publicationStatus = "UNPUBLISHED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '[]'::jsonb")
    private String tags = "[]";

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;
}
