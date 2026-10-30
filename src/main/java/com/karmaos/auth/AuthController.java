package com.karmaos.auth;

import com.karmaos.common.ApiResponse;
import com.karmaos.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean devBypassEnabled;

    public AuthController(AuthService authService,
                          @Value("${app.dev-bypass.enabled:false}") boolean devBypassEnabled) {
        this.authService = authService;
        this.devBypassEnabled = devBypassEnabled;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request)));
    }

    @PostMapping("/dev-bypass")
    public ResponseEntity<ApiResponse<AuthResponse>> devBypass(@Valid @RequestBody LoginRequest request) {
        if (!devBypassEnabled) {
            throw ErrorCode.NOT_FOUND.exception("Not found");
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.devBypass(request)));
    }
}
