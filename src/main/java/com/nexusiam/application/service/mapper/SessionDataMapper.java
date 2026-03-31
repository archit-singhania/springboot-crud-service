package com.nexusiam.application.service.mapper;

import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.core.domain.entity.SSOUserSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionDataMapper {

    private final ObjectMapper objectMapper;

    public void updateSessionWithTokens(SSOUserSession session, String authCode,
                                       String accessToken, String refreshToken,
                                       String idToken, Integer expiresIn,
                                       String jti, String scope) {
        log.debug("Updating session with tokens for profileId: {}", session.getProfileId());

        String existingPkceVerifier = session.getPkceVerifier();
        String existingPkceChallenge = session.getPkceChallenge();
        String existingPkceMethod = session.getPkceMethod();
        Instant existingPkceExpiresAt = session.getPkceVerifierExpiresAt();

        if (existingPkceVerifier != null) {
            log.debug("✓ Preserving PKCE data during token update - Verifier length: {}, Challenge length: {}",
                existingPkceVerifier.length(),
                existingPkceChallenge != null ? existingPkceChallenge.length() : 0);
        } else {
            log.warn("⚠ No existing PKCE verifier to preserve for profileId: {}", session.getProfileId());
        }

        Instant now = Instant.now();
        int tokenExpiry = expiresIn != null ? expiresIn : SSOConstants.DEFAULT_TOKEN_EXPIRY_SECONDS;

        session.setAuthCode(authCode);
        session.setAuthCodeExpiresAt(now.plusSeconds(SSOConstants.AUTH_CODE_EXPIRY_SECONDS));

        session.setSsoAccessToken(accessToken);
        session.setSsoRefreshToken(refreshToken);
        session.setSsoIdToken(idToken);
        session.setSsoTokenIssuedAt(now);
        session.setSsoTokenExpiresAt(now.plusSeconds(tokenExpiry));

        session.setTokenType(SSOConstants.TOKEN_TYPE_BEARER);
        session.setScope(scope);
        session.setJti(jti);
        session.setTokenStatus(SSOConstants.TOKEN_STATUS_ACTIVE);
        session.setLastLoginOn(now);
        session.setLastActivityAt(now);

        if (existingPkceVerifier != null) {
            session.setPkceVerifier(existingPkceVerifier);
            session.setPkceChallenge(existingPkceChallenge);
            session.setPkceMethod(existingPkceMethod);
            session.setPkceVerifierExpiresAt(existingPkceExpiresAt);
            log.debug("✓ PKCE data preserved during token update for profileId: {}", session.getProfileId());
        } else {
            log.warn("⚠ No existing PKCE data to preserve for profileId: {} - this may indicate a storage issue",
                session.getProfileId());
        }

        log.debug("✓ Session tokens updated successfully for profileId: {}", session.getProfileId());
    }

    public void updateSessionMetadata(SSOUserSession session, Map<String, Object> metadata) {
        log.debug("Updating session metadata for profileId: {}", session.getProfileId());

        session.setSessionMetadata(objectMapper.valueToTree(metadata));
        session.setLastActivityAt(Instant.now());

        if (session.getTokenStatus() == null || !session.getTokenStatus().equals("ACTIVE")) {
            log.warn("⚠ Session tokenStatus was '{}' - forcing to ACTIVE", session.getTokenStatus());
            session.setTokenStatus("ACTIVE");
        }

        if (session.getIsActive() == null || !session.getIsActive()) {
            log.warn("⚠ Session isActive was '{}' - forcing to true", session.getIsActive());
            session.setIsActive(true);
        }

        log.debug("✓ Session metadata updated - isActive: {}, tokenStatus: {}",
            session.getIsActive(), session.getTokenStatus());
    }

    public Integer extractExpiresIn(Map<String, Object> tokenResponse) {
        try {
            Object ex = tokenResponse.get("expires_in");
            if (ex instanceof Integer) {
                return (Integer) ex;
            } else if (ex instanceof Number) {
                return ((Number) ex).intValue();
            }
        } catch (Exception e) {
            log.warn("Failed to extract expires_in from token response, using default", e);
        }
        return null;
    }

    public Map<String, Object> buildCustomTokenMetadata(String customAccessToken,
                                                        String customRefreshToken) {
        Instant now = Instant.now();

        return Map.of(
            "customAccessToken", customAccessToken,
            "customRefreshToken", customRefreshToken,
            "customTokenIssuedAt", now.toString(),
            "customTokenExpiresAt", now.plusSeconds(SSOConstants.DEFAULT_TOKEN_EXPIRY_SECONDS).toString()
        );
    }
}
