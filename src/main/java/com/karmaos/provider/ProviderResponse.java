package com.karmaos.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {

    private UUID id;
    private String name;
    private String providerType;
    private String apiEndpoint;
    private List<String> models;
    private List<String> capabilities;
    private String status;
    private boolean apiKeyConfigured;
    private boolean keyRequired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
