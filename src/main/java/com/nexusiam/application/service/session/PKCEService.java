package com.nexusiam.application.service.session;

import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.core.domain.entity.SSOUserSession;
import com.nexusiam.core.domain.repository.SSOUserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PKCEService {

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    private final SSOUserSessionRepository sessionRepo;

    public PKCEService(SSOUserSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    public String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[SSOConstants.PKCE_VERIFIER_LENGTH];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generateCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate code challenge", e);
            throw new RuntimeException("Failed to generate PKCE code challenge", e);
        }
    }

    public boolean storePkceVerifier(String state, String codeVerifier, String codeChallenge,
                                     java.util.function.Supplier<SSOUserSession> tempSessionCreator) {
        boolean storedInRedis = storePkceInRedis(state, codeVerifier);
        boolean storedInDb = storePkceInDatabase(state, codeVerifier, codeChallenge, tempSessionCreator);

        if (storedInRedis && storedInDb) {
            log.info("✓ PKCE stored SIMULTANEOUSLY in both Redis and database for state: {}", state);
        } else if (storedInRedis) {
            log.warn("⚠ PKCE stored in Redis only (database storage failed) for state: {}", state);
        } else if (storedInDb) {
            log.warn("⚠ PKCE stored in database only (Redis unavailable) for state: {}", state);
        } else {
            log.error("❌ PKCE storage FAILED in both Redis and database for state: {}", state);
            return false;
        }

        return true;
    }

    public boolean storePkceInRedis(String state, String codeVerifier) {
        if (!isRedisAvailable()) {
            log.warn("Redis is not available for PKCE storage");
            return false;
        }

        try {
            String key = SSOConstants.PKCE_PREFIX + state;
            redisTemplate.opsForValue().set(
                key,
                Objects.requireNonNull(codeVerifier),
                SSOConstants.PKCE_EXPIRY_SECONDS,
                TimeUnit.SECONDS
            );
            log.debug("✓ PKCE verifier stored in Redis for state: {}", state);
            return true;
        } catch (Exception e) {
            log.warn("Failed to store PKCE in Redis: {}", e.getMessage());
            return false;
        }
    }

    private boolean storePkceInDatabase(String state, String codeVerifier, String codeChallenge,
                                       java.util.function.Supplier<SSOUserSession> tempSessionCreator) {
        try {
            String profileId = SSOConstants.TEMP_SESSION_PREFIX + state;
            Instant expiresAt = Instant.now().plusSeconds(SSOConstants.PKCE_EXPIRY_SECONDS);

            if (codeVerifier == null || codeChallenge == null) {
                log.error("❌ Cannot store null PKCE data - verifier: {}, challenge: {}",
                    codeVerifier != null ? "present" : "NULL",
                    codeChallenge != null ? "present" : "NULL");
                return false;
            }

            Optional<SSOUserSession> existingSession = sessionRepo.findByProfileId(profileId);

            SSOUserSession session;
            if (existingSession.isPresent()) {

                session = existingSession.get();
                log.debug("Updating existing temporary session for state: {}", state);
            } else {

                session = tempSessionCreator.get();
                log.info("Creating new temporary session for state: {}", state);
            }

            session.setPkceVerifier(codeVerifier);
            session.setPkceChallenge(codeChallenge);
            session.setPkceMethod(SSOConstants.PKCE_CHALLENGE_METHOD);
            session.setPkceVerifierExpiresAt(expiresAt);
            session.setUpdatedDate(Instant.now());

            SSOUserSession savedSession = sessionRepo.save(session);

            if (savedSession.getPkceVerifier() == null) {
                log.error("❌ PKCE verifier was NULL after save! Session ID: {}, ProfileId: {}",
                    savedSession.getId(), savedSession.getProfileId());
                return false;
            }

            log.info("✓ PKCE verifier stored in database for state: {} | ProfileId: {} | Verifier length: {} | Challenge length: {}",
                state, profileId, codeVerifier.length(), codeChallenge.length());

            return true;
        } catch (Exception e) {
            log.error("❌ Failed to store PKCE in database for state: {}", state, e);
            return false;
        }
    }

    public String retrievePkceVerifier(String state) {

        String codeVerifier = retrieveFromRedis(state);

        if (codeVerifier != null) {
            log.debug("✓ Retrieved PKCE verifier from Redis for state: {}", state);
            return codeVerifier;
        }

        log.info("PKCE not found in Redis, checking database for state: {}", state);
        codeVerifier = retrieveFromDatabase(state);

        if (codeVerifier != null) {
            log.info("✓ Retrieved PKCE verifier from database for state: {}", state);
            return codeVerifier;
        }

        log.warn("❌ PKCE verifier not found in Redis or database for state: {}", state);
        return null;
    }

    private String retrieveFromRedis(String state) {
        if (!isRedisAvailable()) {
            return null;
        }

        try {
            String key = SSOConstants.PKCE_PREFIX + state;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Failed to retrieve PKCE from Redis: {}", e.getMessage());
            return null;
        }
    }

    private String retrieveFromDatabase(String state) {
        try {
            String profileId = SSOConstants.TEMP_SESSION_PREFIX + state;
            Optional<SSOUserSession> tempSession = sessionRepo.findByProfileId(profileId);

            if (tempSession.isPresent()) {
                SSOUserSession session = tempSession.get();

                if (isPkceVerifierValid(session)) {
                    return session.getPkceVerifier();
                } else {
                    log.warn("⚠ PKCE verifier expired in database for state: {}", state);
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving PKCE from database: {}", e.getMessage(), e);
        }

        return null;
    }

    private boolean isPkceVerifierValid(SSOUserSession session) {
        return session.getPkceVerifier() != null &&
               session.getPkceVerifierExpiresAt() != null &&
               session.getPkceVerifierExpiresAt().isAfter(Instant.now());
    }

    public void deletePkceVerifier(String state) {

        deleteFromRedis(state);

        deleteFromDatabase(state);

        log.info("✓ PKCE verifier deleted from both Redis and database for state: {}", state);
    }

    private void deleteFromRedis(String state) {
        if (!isRedisAvailable()) {
            return;
        }

        try {
            String key = SSOConstants.PKCE_PREFIX + state;
            Boolean deleted = redisTemplate.delete(key);

            if (Boolean.TRUE.equals(deleted)) {
                log.debug("✓ Deleted PKCE verifier from Redis for state: {}", state);
            }
        } catch (Exception e) {
            log.warn("Failed to delete PKCE from Redis: {}", e.getMessage());
        }
    }

    private void deleteFromDatabase(String state) {
        try {
            String profileId = SSOConstants.TEMP_SESSION_PREFIX + state;
            Optional<SSOUserSession> tempSession = sessionRepo.findByProfileId(profileId);

            if (tempSession.isPresent()) {
                SSOUserSession session = tempSession.get();
                session.setPkceVerifier(null);
                session.setPkceChallenge(null);
                session.setPkceVerifierExpiresAt(null);
                sessionRepo.save(session);
                log.debug("✓ Cleared PKCE verifier from database for state: {}", state);
            }
        } catch (Exception e) {
            log.warn("Failed to delete PKCE from database: {}", e.getMessage());
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
