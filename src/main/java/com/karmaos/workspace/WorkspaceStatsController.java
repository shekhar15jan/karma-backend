package com.karmaos.workspace;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class WorkspaceStatsController {

    private final WorkspaceService workspaceService;

    public WorkspaceStatsController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping(ApiConstants.WORKSPACES + "/{id}/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(@PathVariable UUID id) {
        workspaceService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "projectCount", 0,
                "missionCount", 0,
                "artifactCount", 0,
                "lastActivity", java.time.LocalDateTime.now()
        )));
    }
}
