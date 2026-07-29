package com.karmaos.publication;

import com.karmaos.common.BaseEntityLongId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "publications")
@Getter
@Setter
@NoArgsConstructor
public class Publication extends BaseEntityLongId {

    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

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

    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String metadata = "{}";
}
