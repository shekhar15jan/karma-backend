package com.karmaos.prompt;

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
@Table(name = "prompts")
@Getter
@Setter
@NoArgsConstructor
public class Prompt extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '[]'::jsonb")
    private String variables = "[]";

    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    @Column(name = "default_provider_id")
    private UUID defaultProviderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '[]'::jsonb")
    private String tags = "[]";

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;
}
