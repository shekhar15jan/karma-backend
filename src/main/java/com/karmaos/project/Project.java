package com.karmaos.project;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(columnDefinition = "JSONB DEFAULT '[]'::jsonb")
    private String tags = "[]";

    @Column(name = "brand_profile", length = 100)
    private String brandProfile;

    @Column(name = "default_provider_id")
    private UUID defaultProviderId;
}
