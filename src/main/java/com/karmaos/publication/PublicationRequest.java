package com.karmaos.publication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicationRequest {

    @NotNull
    private UUID artifactId;

    @NotBlank
    private String destination;

    private Object metadata;
}
