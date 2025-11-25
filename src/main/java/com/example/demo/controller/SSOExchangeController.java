package com.example.demo.controller;

import com.example.demo.dto.LoginResponse;
import com.example.demo.model.SSOExchangeMaster;
import com.example.demo.service.SSOExchangeService;
import com.example.demo.service.JwksService;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
@Slf4j
public class SSOExchangeController {

    private final SSOExchangeService ssoService;
    private final JwksService jwksService;

    @GetMapping("/authorize")
    public ResponseEntity<Map<String, String>> initiateLogin(@RequestParam String state) {
        log.info("Authorization request received with state: {}", state);
        Map<String, String> response = ssoService.requestAuthCode(state);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/callback")
    public ResponseEntity<LoginResponse> handleCallback(
            @RequestParam String code,
            @RequestParam String state) {
        log.info("Callback received with code and state");
        LoginResponse response = ssoService.exchangeAuthCode(code, state);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/profile")
    public ResponseEntity<SSOExchangeMaster> getProfile(
            @RequestHeader("Authorization") String authorization) {

        if (!authorization.startsWith("Bearer ")) {
            log.warn("Invalid authorization header format");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String accessToken = authorization.substring(7);
        SSOExchangeMaster profile = ssoService.getProfile(accessToken);
        log.info("Profile retrieved successfully for user: {}", profile.getProfileId());

        return ResponseEntity.ok(profile);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
            @RequestBody Map<String, String> request) {

        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Token refresh requested");
        LoginResponse response = ssoService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/org/{orgId}")
    public ResponseEntity<SSOExchangeMaster> getOrganizationProfile(
            @PathVariable String orgId,
            @RequestHeader("Authorization") String authorization) {

        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String serviceToken = authorization.substring(7);
        log.info("Organization profile requested for: {}", orgId);

        SSOExchangeMaster orgProfile = ssoService.getOrganizationProfile(orgId, serviceToken);
        return ResponseEntity.ok(orgProfile);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "SSO Exchange",
                "message", "SSO integration is running"
        ));
    }

    @GetMapping("/validate-okta-token")
    public ResponseEntity<Map<String, Object>> validateOktaToken(
            @RequestHeader("Authorization") String authorization) {

        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String oktaToken = authorization.substring(7);

        try {
            JWTClaimsSet claims = jwksService.validateAndParseToken(oktaToken);

            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "profileId", claims.getSubject(),
                    "issuer", claims.getIssuer(),
                    "audience", claims.getAudience(),
                    "expiresAt", claims.getExpirationTime().toString()
            ));
        } catch (Exception e) {
            log.error("Okta token validation failed", e);
            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", e.getMessage()
            ));
        }
    }
}
