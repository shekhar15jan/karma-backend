package com.karmaos.provider;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.PROVIDERS)
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProviderResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(providerService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(providerService.getById(id)));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(providerService.testConnection(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProviderResponse>> create(@Valid @RequestBody ProviderRequest request) {
        return ResponseEntity.ok(ApiResponse.created(providerService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProviderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(providerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        providerService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Provider deleted"));
    }
}
