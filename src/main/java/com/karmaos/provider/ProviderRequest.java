package com.karmaos.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 50)
    private String providerType;

    @Size(max = 4096)
    private String apiKey;

    @Size(max = 500)
    private String apiEndpoint;
    private Object configuration;
    private java.util.List<String> models;
}
