package com.karmaos.project;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.PROJECTS)
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> list(@RequestParam UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.listByWorkspace(workspaceId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.created(projectService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Project deleted"));
    }
}
