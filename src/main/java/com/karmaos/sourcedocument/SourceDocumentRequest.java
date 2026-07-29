package com.karmaos.sourcedocument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceDocumentRequest {

    private UUID missionId;

    @NotBlank
    @Size(max = 255)
    private String filename;

    @NotBlank
    private String content;

    private String format;
}
