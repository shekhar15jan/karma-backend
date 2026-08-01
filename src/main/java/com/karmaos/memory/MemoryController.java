package com.karmaos.memory;

import com.karmaos.auth.UserPrincipal;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {

    private final KarmaMemoryService memoryService;
    private final KarmaUserMemoryRepository memoryRepository;

    public MemoryController(KarmaMemoryService memoryService, KarmaUserMemoryRepository memoryRepository) {
        this.memoryService = memoryService;
        this.memoryRepository = memoryRepository;
    }

    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> feedback(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, Object> body) {
        UUID eventId = parseUuid(body.get("eventId"));
        boolean success = Boolean.TRUE.equals(body.get("success"));
        String note = body.get("note") != null ? String.valueOf(body.get("note")) : null;
        memoryService.recordActionFeedback(user.getId(), eventId, success, note);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("recorded", true)));
    }

    @PostMapping("/remember")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remember(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, Object> body) {
        String key = body.get("key") != null ? String.valueOf(body.get("key")) : null;
        String value = body.get("value") != null ? String.valueOf(body.get("value")) : null;
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("Both key and value are required", 400));
        }
        memoryService.remember(user.getId(), key, value, "EXPLICIT");
        return ResponseEntity.ok(ApiResponse.ok(Map.of("remembered", true, "key", key, "value", value)));
    }

    @GetMapping("/memories")
    public ResponseEntity<ApiResponse<List<KarmaUserMemory>>> memories(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.ok(memoryRepository.findByUserIdOrderByConfidenceDesc(user.getId())));
    }

    private UUID parseUuid(Object value) {
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
