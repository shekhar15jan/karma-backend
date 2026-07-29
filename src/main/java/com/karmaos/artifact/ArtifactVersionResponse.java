package com.karmaos.artifact;

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
public class ArtifactVersionResponse {
    private UUID id;
    private Long artifactId;
    private int version;
    private String contentText;
    private String contentType;
    private String storageLocation;
    private String checksum;
    private long size;
    private String metadata;
    private LocalDateTime createdAt;
}
