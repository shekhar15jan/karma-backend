package com.karmaos.publication;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "publications")
@Getter
@Setter
@NoArgsConstructor
public class Publication extends BaseEntity {

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(nullable = false, length = 50)
    private String destination;

    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(length = 500)
    private String url;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String metadata = "{}";
}
