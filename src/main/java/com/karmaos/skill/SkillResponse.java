package com.karmaos.skill;

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
public class SkillResponse {

    private UUID id;
    private String name;
    private String description;
    private String category;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
