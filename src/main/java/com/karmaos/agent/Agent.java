package com.karmaos.agent;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "agents")
@Getter
@Setter
@NoArgsConstructor
public class Agent extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String icon;

    @Column(name = "default_prompt_id")
    private UUID defaultPromptId;

    @Column(name = "default_provider_id")
    private UUID defaultProviderId;

    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String configuration = "{}";
}
