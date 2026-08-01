package com.karmaos.auth;

import com.karmaos.common.ApiConstants;
import com.karmaos.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> users() {
        var users = userRepository.findAll()
                .stream()
                .map(u -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", u.getId().toString());
                    entry.put("email", u.getEmail());
                    entry.put("displayName", u.getDisplayName());
                    entry.put("role", u.getRole() != null ? u.getRole() : "USER");
                    entry.put("isActive", u.getDeletedAt() == null);
                    entry.put("lastLoginAt", u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null);
                    return entry;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> roles() {
        List<Map<String, Object>> roles = List.of(
                Map.of(
                        "id", "role-admin",
                        "name", "ADMIN",
                        "description", "Full system access including users, roles and settings",
                        "permissions", List.of("workflows:read", "workflows:write", "workflows:execute",
                                "providers:read", "providers:write", "analytics:read",
                                "admin:users", "admin:roles", "admin:settings", "admin:audit")),
                Map.of(
                        "id", "role-operator",
                        "name", "OPERATOR",
                        "description", "Run missions, review artifacts and manage publishing",
                        "permissions", List.of("workflows:read", "workflows:execute",
                                "providers:read", "analytics:read")),
                Map.of(
                        "id", "role-reviewer",
                        "name", "REVIEWER",
                        "description", "Approve or reject generated artifacts before publishing",
                        "permissions", List.of("workflows:read", "analytics:read")),
                Map.of(
                        "id", "role-user",
                        "name", "USER",
                        "description", "Standard user with read access",
                        "permissions", List.of("workflows:read"))
        );
        return ResponseEntity.ok(ApiResponse.ok(roles));
    }
}
