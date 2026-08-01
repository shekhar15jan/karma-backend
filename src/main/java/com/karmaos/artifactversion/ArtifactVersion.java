package com.karmaos.artifactversion;

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
@Table(name = "artifact_versions")
@Getter
@Setter
@NoArgsConstructor
public class ArtifactVersion extends BaseEntity {

    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    @Column(nullable = false)
    private int version;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "storage_location", columnDefinition = "TEXT")
    private String storageLocation;

    @Column(length = 64)
    private String checksum;

    private long size = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String metadata = "{}";

    @Column(name = "created_by")
    private UUID createdBy;
}
