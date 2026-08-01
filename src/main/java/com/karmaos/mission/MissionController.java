package com.karmaos.mission;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
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

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.MISSIONS)
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MissionResponse>>> list(
            @RequestParam UUID projectId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.listByProject(projectId, user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MissionResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.getById(id, user)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MissionResponse>> create(
            @Valid @RequestBody MissionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.created(missionService.create(request, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MissionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody MissionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.update(id, request, user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        missionService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.ok(null, "Mission deleted"));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<MissionResponse>> execute(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.execute(id, user)));
    }
}
