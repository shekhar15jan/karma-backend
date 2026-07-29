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
public class DashboardController {

    @GetMapping(ApiConstants.DASHBOARD + "/{workspaceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "projectCount", 0,
                "missionCount", 0,
                "artifactCount", 0,
                "recentActivity", java.util.Collections.emptyList()
        )));
    }
}
