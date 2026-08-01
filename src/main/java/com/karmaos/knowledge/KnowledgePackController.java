package com.karmaos.knowledge;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.KNOWLEDGE)
public class KnowledgePackController {

    private final KnowledgePackService knowledgePackService;

    public KnowledgePackController(KnowledgePackService knowledgePackService) {
        this.knowledgePackService = knowledgePackService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<KnowledgePackResponse>>> list(
            @RequestParam(required = false) UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal user) {
        if (workspaceId != null) {
            return ResponseEntity.ok(ApiResponse.ok(knowledgePackService.listByWorkspace(workspaceId, user.getId())));
        }
        return ResponseEntity.ok(ApiResponse.ok(knowledgePackService.listAll(user.getId())));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgePackService.uploadFile(file, workspaceId, user.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgePackResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgePackService.getById(id, user.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<KnowledgePackResponse>> create(
            @Valid @RequestBody KnowledgePackRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.created(knowledgePackService.create(request, user.getId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgePackResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody KnowledgePackRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgePackService.update(id, request, user.getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        knowledgePackService.delete(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Knowledge pack deleted"));
    }
}
