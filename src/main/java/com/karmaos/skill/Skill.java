package com.karmaos.skill;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "validation_rules", columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String validationRules = "{}";

    @Column(name = "execution_parameters", columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String executionParameters = "{}";
}
