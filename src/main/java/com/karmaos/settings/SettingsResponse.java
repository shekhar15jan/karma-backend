package com.karmaos.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID userId;
    private String settingsData;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
