package com.karmaos.workspace;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import com.karmaos.auth.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping(ApiConstants.WORKSPACES)
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> list(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.listByUser(user)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
            @Valid @RequestBody WorkspaceRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.created(workspaceService.create(request, user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.getById(id, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody WorkspaceRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.update(id, request, user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        workspaceService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.ok(null, "Workspace deleted"));
    }
}
