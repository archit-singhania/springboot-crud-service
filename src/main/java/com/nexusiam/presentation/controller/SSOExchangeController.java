package com.nexusiam.presentation.controller;

import com.nexusiam.application.dto.response.LoginResponse;
import com.nexusiam.application.dto.request.RoleSwitchRequest;
import com.nexusiam.application.dto.response.RoleSwitchResponse;

import com.nexusiam.application.service.token.CustomTokenService;
import com.nexusiam.application.service.authorization.RoleSwitchService;
import com.nexusiam.application.service.authentication.SSOExchangeService;
import com.nexusiam.application.service.session.SessionManagementService;

import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/exchange/v1/sso")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SSO Exchange", description = "SSO authentication APIs for SSO users (buyer, seller)")
public class SSOExchangeController {
    private final SSOExchangeService ssoService;
    private final CustomTokenService customTokenService;
    private final SessionManagementService sessionManagementService;
    private final RoleSwitchService roleSwitchService;

    @GetMapping("/authorize")
    public ResponseEntity<Map<String, String>> initiateLogin(@RequestParam String state) {
        Map<String, String> response = ssoService.requestAuthCode(state);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/callback")
    public ResponseEntity<LoginResponse> handleCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        LoginResponse response = ssoService.exchangeAuthCode(code, state, ipAddress, userAgent, request);
        return ResponseEntity.ok(response);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('BUYER', 'SELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader("Authorization") String authorization) {

        if (!authorization.startsWith("Bearer ")) {
            log.warn("Invalid authorization header format");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String accessToken = authorization.substring(7);

        if (!sessionManagementService.validateSSOSession(accessToken)) {
            log.warn("Session validation failed for token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Session is no longer valid"));
        }

        Map<String, Object> profile = ssoService.getProfile(accessToken);
        return ResponseEntity.ok(profile);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/profile/detailed")
    @PreAuthorize("hasAnyAuthority('BUYER', 'SELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getDetailedProfile(
            @RequestHeader("Authorization") String authorization) throws ParseException {

        String accessToken = authorization.substring(7);

        if (!sessionManagementService.validateSSOSession(accessToken)) {
            log.warn("Session validation failed for detailed profile");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Session is no longer valid"));
        }

        JWTClaimsSet claims = customTokenService.validateAndParseToken(accessToken);

        Map<String, Object> response = new HashMap<>();
        response.put("profile_id", claims.getSubject());
        response.put("grp_id", claims.getClaim("grp_id"));
        response.put("role", claims.getClaim("role"));
        response.put("user_type", claims.getClaim("user_type"));
        response.put("current_role", claims.getClaim("current_role"));
        response.put("available_roles", claims.getClaim("available_roles"));
        response.put("token_type", claims.getClaim("token_type"));
        response.put("issued_at", claims.getIssueTime());
        response.put("expires_at", claims.getExpirationTime());

        Map<String, Object> registrations = claims.getJSONObjectClaim("registrations");
        response.put("registrations", registrations);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON != null
                ? MediaType.APPLICATION_JSON
                : MediaType.parseMediaType("application/json"))
            .body(response);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/switch-role")
    @PreAuthorize("hasAnyAuthority('BUYER', 'SELLER')")
    public ResponseEntity<RoleSwitchResponse> switchRole(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody RoleSwitchRequest request) {

        try {
            String accessToken = authorization.substring(7);

            if (!sessionManagementService.validateSSOSession(accessToken)) {
                log.warn("Session validation failed for role switch");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            RoleSwitchResponse response = roleSwitchService.switchRole(
                accessToken,
                request.getNewRole()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Role switch failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/introspect")
    public ResponseEntity<Map<String, Object>> introspectToken(
            @RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Token is required"));
        }

        Map<String, Object> result = ssoService.introspectToken(token);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Refresh token is required"));
        }

        try {
            String ipAddress = httpRequest.getRemoteAddr();
            String userAgent = httpRequest.getHeader("User-Agent");
            LoginResponse response = ssoService.refreshAccessToken(refreshToken, ipAddress, userAgent);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Refresh token failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "error", e.getMessage(),
                    "errorType", e.getClass().getSimpleName()
                ));
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    @PreAuthorize("hasAnyAuthority('BUYER', 'SELLER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.substring(7);

            sessionManagementService.invalidateSSOSession(token);

            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));

        } catch (Exception e) {
            log.error("Logout failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Logout failed: " + e.getMessage()));
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

            boolean isValid = sessionManagementService.validateSSOSession(token);

            if (isValid) {
                JWTClaimsSet claims = customTokenService.validateAndParseToken(token);

                Map<String, Object> response = new HashMap<>();
                response.put("valid", true);
                response.put("profile_id", claims.getSubject());
                response.put("grp_id", claims.getClaim("grp_id"));
                response.put("role", claims.getClaim("role"));
                response.put("user_type", claims.getClaim("user_type"));
                response.put("current_role", claims.getClaim("current_role"));
                response.put("available_roles", claims.getClaim("available_roles"));

                return ResponseEntity.ok(response);
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
}
