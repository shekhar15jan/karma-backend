package com.karmaos.knowledge;

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
public class KnowledgePackResponse {

    private UUID id;
    private UUID workspaceId;
    private String name;
    private String description;
    private String category;
    private String status;
    private int documentCount;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
