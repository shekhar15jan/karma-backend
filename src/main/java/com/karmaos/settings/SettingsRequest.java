package com.karmaos.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsRequest {

    private UUID workspaceId;
    private UUID userId;

    private String settingsData;
}
