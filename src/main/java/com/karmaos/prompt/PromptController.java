package com.karmaos.prompt;

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
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.PROMPTS)
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromptResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(promptService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(promptService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PromptResponse>> create(@Valid @RequestBody PromptRequest request) {
        return ResponseEntity.ok(ApiResponse.created(promptService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PromptRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(promptService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        promptService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Prompt deleted"));
    }
}
