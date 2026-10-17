package com.karmaos.auth;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final Environment environment;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider,
                       Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.environment = environment;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ErrorCode.DUPLICATE_EMAIL.exception("Email already registered: " + request.getEmail());
        }

        User user = new User(request.getEmail(), passwordEncoder.encode(request.getPassword()), request.getDisplayName());
        user = userRepository.save(user);

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), java.util.List.of("ROLE_" + user.getRole()));
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("User not found"));

        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), java.util.List.of("ROLE_" + user.getRole()));
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public AuthResponse devBypass(LoginRequest request) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            // Unreachable in production: no password check, silently grants ADMIN on first use.
            throw ErrorCode.NOT_FOUND.exception("Not found");
        }
        User user = userRepository.findByEmail(request.getEmail()).orElseGet(() -> {
            User newUser = new User(request.getEmail(), passwordEncoder.encode(request.getPassword()), "Dev User");
            newUser.setRole("ADMIN");
            return userRepository.save(newUser);
        });

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), java.util.List.of("ROLE_" + user.getRole()));
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    /** Short-lived token for URLs that can't carry an Authorization header (SSE, media stream). */
    public StreamTokenResponse issueStreamToken(UserPrincipal principal) {
        java.util.List<String> roles = principal.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toList());
        String token = tokenProvider.generateStreamToken(principal.getId(), principal.getUsername(), roles);
        return new StreamTokenResponse(token, 15 * 60);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!tokenProvider.validateRefreshToken(request.getRefreshToken())) {
            throw ErrorCode.TOKEN_INVALID.exception("Invalid refresh token");
        }

        var userId = tokenProvider.getUserIdFromToken(request.getRefreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("User not found"));

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), java.util.List.of("ROLE_" + user.getRole()));
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
