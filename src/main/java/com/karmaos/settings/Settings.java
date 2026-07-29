package com.karmaos.settings;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
public class Settings extends BaseEntity {

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "settings_data", nullable = false, columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String settingsData = "{}";
}
