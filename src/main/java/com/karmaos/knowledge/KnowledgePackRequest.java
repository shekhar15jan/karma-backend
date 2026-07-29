package com.karmaos.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePackRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 2000)
    private String description;

    private String category;
    private List<String> tags;
}
