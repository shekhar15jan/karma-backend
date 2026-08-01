package com.karmaos.publication;

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
public class PublicationResponse {

    private UUID id;
    private UUID artifactId;
    private String destination;
    private String status;
    private LocalDateTime publishedAt;
    private String url;
    private String errorMessage;
    private LocalDateTime createdAt;
}
