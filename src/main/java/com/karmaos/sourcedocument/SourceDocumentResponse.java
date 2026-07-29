package com.karmaos.sourcedocument;

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
public class SourceDocumentResponse {

    private UUID id;
    private UUID missionId;
    private String filename;
    private String content;
    private String format;
    private long size;
    private String checksum;
    private LocalDateTime createdAt;
}
