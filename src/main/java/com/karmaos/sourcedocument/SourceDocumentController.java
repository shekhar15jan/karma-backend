package com.karmaos.sourcedocument;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.SOURCE_DOCUMENTS)
public class SourceDocumentController {

    private final SourceDocumentService sourceDocumentService;

    public SourceDocumentController(SourceDocumentService sourceDocumentService) {
        this.sourceDocumentService = sourceDocumentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SourceDocumentResponse>>> list(@RequestParam UUID missionId) {
        return ResponseEntity.ok(ApiResponse.ok(sourceDocumentService.listByMission(missionId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SourceDocumentResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(sourceDocumentService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SourceDocumentResponse>> create(@Valid @RequestBody SourceDocumentRequest request) {
        return ResponseEntity.ok(ApiResponse.created(sourceDocumentService.create(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        sourceDocumentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Source document deleted"));
    }
}
