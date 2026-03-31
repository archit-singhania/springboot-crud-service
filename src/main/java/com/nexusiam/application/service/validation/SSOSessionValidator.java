package com.nexusiam.application.service.validation;

import com.nexusiam.presentation.exception.SSOSessionException;
import com.nexusiam.core.domain.entity.SSOUserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SSOSessionValidator {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String JTI_PREFIX = "jti:";
    private static final long JTI_EXPIRY_HOURS = 24;

    public SSOSessionValidator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void validateSession(SSOUserSession session, String requestId) {
        log.debug("[RequestID: {}] Validating session for profileId: {}", requestId, session.getProfileId());

        if (session.getAuthCodeExpiresAt() != null && session.getAuthCodeExpiresAt().isBefore(Instant.now())) {
            log.error("[RequestID: {}] Auth code expired for profileId: {}", requestId, session.getProfileId());
            throw new SSOSessionException("Authorization code has expired");
        }

        log.debug("[RequestID: {}] Session validation passed for profileId: {}", requestId, session.getProfileId());
    }

    public void validateSession(SSOUserSession session) {
        validateSession(session, "N/A");
    }

    public void validateTokenExpiry(SSOUserSession session, String requestId) {
        if (session.getSsoTokenExpiresAt() == null || session.getSsoTokenExpiresAt().isBefore(Instant.now())) {
            log.error("[RequestID: {}] Token expired for profileId: {}. Expiry: {}, Current time: {}",
                    requestId, session.getProfileId(), session.getSsoTokenExpiresAt(), Instant.now());
            throw new SSOSessionException("SSO token has expired or expiry time is invalid");
        }
        log.debug("[RequestID: {}] Token expiry validation passed for profileId: {}", requestId, session.getProfileId());
    }

    public boolean isSessionActive(SSOUserSession session) {
        return session != null &&
               session.getSsoAccessToken() != null &&
               session.getSsoTokenExpiresAt() != null &&
               session.getSsoTokenExpiresAt().isAfter(Instant.now());
    }

    public boolean needsRefresh(SSOUserSession session, int refreshThresholdSeconds) {
        if (session == null || session.getSsoTokenExpiresAt() == null) {
            return true;
        }

        Instant refreshThreshold = Instant.now().plusSeconds(refreshThresholdSeconds);
        return session.getSsoTokenExpiresAt().isBefore(refreshThreshold);
    }

    public boolean isOktaTokenExpired(SSOUserSession session) {
        if (session == null || session.getSsoTokenExpiresAt() == null) {
            return true;
        }
        return session.getSsoTokenExpiresAt().isBefore(Instant.now());
    }

    public boolean isTokenReplayed(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }

        String key = JTI_PREFIX + jti;
        Boolean exists = redisTemplate.hasKey(key);

        if (Boolean.TRUE.equals(exists)) {
            log.warn("Token replay detected for JTI: {}", jti);
            return true;
        }

        redisTemplate.opsForValue().set(key, "used", JTI_EXPIRY_HOURS, TimeUnit.HOURS);
        return false;
    }
}
