package com.karmaos.promptversion;

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
@Table(name = "prompt_versions")
@Getter
@Setter
@NoArgsConstructor
public class PromptVersion extends BaseEntity {

    @Column(name = "prompt_id", nullable = false)
    private UUID promptId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '[]'::jsonb")
    private String variables = "[]";

    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    @Column(name = "created_by")
    private UUID createdBy;
}
