package com.karmaos.prompt;

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
public class PromptVersionResponse {
    private UUID id;
    private UUID promptId;
    private int version;
    private String content;
    private List<String> variables;
    private String status;
    private LocalDateTime createdAt;
}
