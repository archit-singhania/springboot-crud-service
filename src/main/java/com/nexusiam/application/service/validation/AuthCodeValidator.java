package com.nexusiam.application.service.validation;

import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.shared.constants.SSOErrorCode;
import com.nexusiam.presentation.exception.SSOAuthenticationException;
import com.nexusiam.core.domain.entity.SSOUserSession;
import com.nexusiam.core.domain.repository.SSOUserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
public class AuthCodeValidator {

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    private final SSOUserSessionRepository sessionRepo;

    public AuthCodeValidator(SSOUserSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    public String retrieveAndValidateCodeVerifier(String state) {
        log.debug("Retrieving PKCE verifier for state: {}", state);

        String codeVerifier = retrieveFromRedis(state);

        if (codeVerifier != null) {
            log.info("PKCE verifier retrieved from Redis for state: {}", state);
            return codeVerifier;
        }

        log.info("Redis unavailable or PKCE not found, trying database for state: {}", state);
        codeVerifier = retrieveFromDatabase(state);

        if (codeVerifier == null) {
            log.error("PKCE verifier not found or expired for state: {}", state);
            throw new SSOAuthenticationException(
                SSOErrorCode.PKCE_VALIDATION_FAILED
            );
        }

        log.info("Successfully retrieved PKCE verifier from database for state: {}", state);
        return codeVerifier;
    }

    public void cleanupPkceVerifier(String state) {
        log.debug("Cleaning up PKCE verifier for state: {}", state);

        boolean redisCleanup = cleanupFromRedis(state);

        boolean dbCleanup = cleanupFromDatabase(state);

        if (redisCleanup || dbCleanup) {
            log.info("PKCE verifier cleanup completed for state: {} (Redis: {}, DB: {})",
                    state, redisCleanup, dbCleanup);
        } else {
            log.warn("PKCE verifier not found in Redis or database for cleanup: {}", state);
        }
    }

    private String retrieveFromRedis(String state) {
        if (!isRedisAvailable()) {
            log.debug("Redis is not available, skipping Redis retrieval");
            return null;
        }

        try {
            String key = SSOConstants.PKCE_PREFIX + state;
            String codeVerifier = redisTemplate.opsForValue().get(key);

            if (codeVerifier != null) {
                log.debug("Retrieved PKCE verifier from Redis for state: {}", state);
            } else {
                log.debug("PKCE verifier not found in Redis for state: {}", state);
            }

            return codeVerifier;
        } catch (Exception e) {
            log.warn("Failed to retrieve PKCE from Redis for state: {} - Error: {}",
                    state, e.getMessage());
            return null;
        }
    }

    private String retrieveFromDatabase(String state) {
        try {
            String profileId = SSOConstants.TEMP_SESSION_PREFIX + state;
            Optional<SSOUserSession> tempSession = sessionRepo.findByProfileId(profileId);

            if (tempSession.isEmpty()) {
                log.debug("Temporary session not found in database for state: {}", state);
                return null;
            }

            SSOUserSession session = tempSession.get();

            if (!isPkceVerifierValid(session)) {
                log.warn("PKCE verifier expired or invalid in database for state: {}", state);
                return null;
            }

            log.info("Retrieved valid PKCE verifier from database for state: {}", state);
            return session.getPkceVerifier();

        } catch (Exception e) {
            log.error("Error retrieving PKCE from database for state: {} - Error: {}",
                    state, e.getMessage(), e);
            return null;
        }
    }

    private boolean isPkceVerifierValid(SSOUserSession session) {
        if (session.getPkceVerifier() == null) {
            log.debug("PKCE verifier is null in session");
            return false;
        }

        if (session.getPkceVerifierExpiresAt() == null) {
            log.warn("PKCE verifier expires_at is null in session");
            return false;
        }

        Instant now = Instant.now();
        if (session.getPkceVerifierExpiresAt().isBefore(now)) {
            log.warn("PKCE verifier expired at {} (current time: {})",
                    session.getPkceVerifierExpiresAt(), now);
            return false;
        }

        return true;
    }

    private boolean cleanupFromRedis(String state) {
        if (!isRedisAvailable()) {
            log.debug("Redis is not available, skipping Redis cleanup");
            return false;
        }

        try {
            String key = SSOConstants.PKCE_PREFIX + state;
            Boolean deleted = redisTemplate.delete(key);

            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Deleted PKCE verifier from Redis for state: {}", state);
                return true;
            } else {
                log.debug("PKCE verifier not found in Redis for cleanup: {}", state);
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to delete PKCE from Redis for state: {} - Error: {}",
                    state, e.getMessage());
            return false;
        }
    }

    private boolean cleanupFromDatabase(String state) {
        try {
            String profileId = SSOConstants.TEMP_SESSION_PREFIX + state;
            Optional<SSOUserSession> tempSession = sessionRepo.findByProfileId(profileId);

            if (tempSession.isEmpty()) {
                log.debug("Temporary session not found in database for cleanup: {}", state);
                return false;
            }

            SSOUserSession session = tempSession.get();

            sessionRepo.delete(session);
            log.debug("Deleted temporary session from database for state: {}", state);
            return true;

        } catch (Exception e) {
            log.warn("Failed to cleanup PKCE from database for state: {} - Error: {}",
                    state, e.getMessage());
            return false;
        }
    }

    private boolean isRedisAvailable() {
        if (redisTemplate == null) {
            log.debug("RedisTemplate is null - Redis is disabled");
            return false;
        }
        try {
            redisTemplate.hasKey(SSOConstants.HEALTH_CHECK_KEY);
            return true;
        } catch (Exception e) {
            log.debug("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }
}
