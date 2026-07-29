package com.karmaos.publication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicationRequest {

    @NotNull
    private Long artifactId;

    @NotBlank
    private String destination;

    private Object metadata;
}
