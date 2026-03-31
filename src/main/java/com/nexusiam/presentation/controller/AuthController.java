package com.nexusiam.presentation.controller;

import com.nexusiam.application.dto.response.ApiResponse;
import com.nexusiam.application.dto.request.LoginRequest;
import com.nexusiam.application.dto.request.RefreshTokenRequest;
import com.nexusiam.application.dto.response.InternalUserResponse;
import com.nexusiam.core.domain.entity.InternalUser;
import com.nexusiam.core.domain.entity.ApiSource;
import com.nexusiam.core.domain.repository.InternalUserRepository;
import com.nexusiam.core.domain.repository.UserApiMappingRepository;
import com.nexusiam.core.domain.repository.ApiSourceRepository;
import com.nexusiam.core.domain.entity.UserApiMapping;
import com.nexusiam.application.service.utility.DeviceFingerprintService;
import com.nexusiam.application.service.session.SessionManagementService;
import com.nexusiam.infrastructure.util.JwtTokenUtil;
import com.nexusiam.application.service.user.InternalUserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/exchange/v1/int/auth")
@RequiredArgsConstructor
@Tag(name = "Internal Authentication", description = "Authentication APIs for internal users")
public class AuthController {

    private final InternalUserRepository userRepo;
    private final UserApiMappingRepository userApiMappingRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final PasswordEncoder encoder;
    private final JwtTokenUtil jwt;
    private final InternalUserService userService;
    private final SessionManagementService sessionManagementService;
    private final DeviceFingerprintService deviceFingerprintService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody InternalUser user) {

        user.setPassword(encoder.encode(user.getPassword()));

        if (user.getIsDeleted() == null) {
            user.setIsDeleted(false);
        }

        userRepo.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Internal user registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        try {
            InternalUser user = userRepo.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "User account is deactivated"));
            }

            if (!encoder.matches(loginRequest.getPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
            }

            String deviceFingerprint = deviceFingerprintService.generateDeviceFingerprint(request);
            String browserFingerprint = deviceFingerprintService.generateBrowserFingerprint(request);

            String role = user.getRole() != null ? user.getRole() : "ADMIN";

            sessionManagementService.trackInternalDevice(user.getEmail(), deviceFingerprint, browserFingerprint, request);

            List<String> compactPermissions = buildCompactPermissions(user.getId());
            String accessToken = jwt.generateAccessTokenWithCompactPermissions(
                user.getEmail(),
                user.getId(),
                role,
                compactPermissions
            );
            String refreshToken = jwt.generateRefreshToken(user.getEmail(), role);

            sessionManagementService.storeUserActiveToken(user.getEmail(), accessToken, false);

            String userType = user.getRole() != null ? user.getRole().toLowerCase().replace("_", " ") : "admin";

            return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "email", user.getEmail(),
                "role", role,
                "userType", userType,
                "tokenType", "Bearer"
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/logout")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.substring(7);

            sessionManagementService.invalidateInternalSession(token);

            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<InternalUserResponse>> getProfile(
            @RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.substring(7);

            if (!sessionManagementService.validateInternalSession(token)) {
                ApiResponse<InternalUserResponse> errorResponse = new ApiResponse<>(
                    "error",
                    "Session is no longer valid",
                    null,
                    HttpStatus.UNAUTHORIZED.value()
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            org.springframework.security.core.userdetails.User userDetails =
                (org.springframework.security.core.userdetails.User) SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getPrincipal();

            String email = userDetails.getUsername();

            InternalUserResponse user = userService.getUserByEmail(email);
            ApiResponse<InternalUserResponse> apiResponse = new ApiResponse<>(
                    "success",
                    "Profile retrieved successfully",
                    user,
                    HttpStatus.OK.value()
            );
            return ResponseEntity.ok(apiResponse);

        } catch (Exception e) {
            ApiResponse<InternalUserResponse> errorResponse = new ApiResponse<>(
                "error",
                "Failed to retrieve profile: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            String refreshToken = request.getRefreshToken();

            if (!jwt.isTokenValid(refreshToken) || !jwt.isRefreshToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired refresh token"));
            }

            String email = jwt.extractEmail(refreshToken);
            String role = jwt.getRole(refreshToken);

            InternalUser user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "User account is deactivated"));
            }

            List<String> compactPermissions = buildCompactPermissions(user.getId());
            String newAccessToken = jwt.generateAccessTokenWithCompactPermissions(
                email,
                user.getId(),
                role,
                compactPermissions
            );

            return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", refreshToken,
                "tokenType", "Bearer"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token refresh failed: " + e.getMessage()));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");

            if (token == null || token.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", "Token is required"));
            }

            boolean isValid = sessionManagementService.validateInternalSession(token);

            if (isValid) {
                String email = jwt.extractEmail(token);
                String role = jwt.getRole(token);

                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "email", email,
                    "role", role
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", "Token is invalid or expired"
                ));
            }

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "valid", false,
                "error", e.getMessage()
            ));
        }
    }

    private List<String> buildCompactPermissions(Long userId) {
        try {
            List<UserApiMapping> mappings =
                userApiMappingRepository.findUserAccessibleApisWithDetails(userId);

            List<String> compactPermissions = new ArrayList<>();

            for (UserApiMapping mapping : mappings) {
                ApiSource apiSource = apiSourceRepository.findById(mapping.getApiSourceId()).orElse(null);
                if (apiSource != null) {
                    String method = apiSource.getApiMethod();
                    String path = apiSource.getApiPath();
                    compactPermissions.add(method.toUpperCase() + ":" + path);
                }
            }

            return compactPermissions;
        } catch (Exception e) {
            return List.of();
        }
    }
}
