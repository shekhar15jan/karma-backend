package com.karmaos.skill;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "skills")
@Getter
@Setter
@NoArgsConstructor
public class Skill extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules", columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String validationRules = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_parameters", columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String executionParameters = "{}";

    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;
}
