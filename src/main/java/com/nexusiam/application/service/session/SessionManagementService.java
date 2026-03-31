package com.nexusiam.application.service.session;

import com.nexusiam.application.service.token.CustomTokenService;
import com.nexusiam.infrastructure.util.JwtTokenUtil;
import com.nexusiam.core.domain.entity.SSOUserSession;
import com.nexusiam.core.domain.entity.InternalUser;
import com.nexusiam.core.domain.repository.SSOUserSessionRepository;
import com.nexusiam.core.domain.repository.InternalUserRepository;
import com.nimbusds.jwt.JWTClaimsSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

@Service
@Slf4j
public class SessionManagementService {

    private final SSOUserSessionRepository ssoSessionRepo;
    private final InternalUserRepository userRepo;
    private final RedisTemplate<String, String> redisTemplate;
    private final CustomTokenService customTokenService;
    private final JwtTokenUtil jwtTokenUtil;

    private static final String DEVICE_TRACKING_PREFIX = "device:";
    private static final String INVALIDATED_TOKEN_PREFIX = "invalidated:";

    public SessionManagementService(
            SSOUserSessionRepository ssoSessionRepo,
            InternalUserRepository userRepo,
            @Autowired(required = false) RedisTemplate<String, String> redisTemplate,
            CustomTokenService customTokenService,
            JwtTokenUtil jwtTokenUtil) {
        this.ssoSessionRepo = ssoSessionRepo;
        this.userRepo = userRepo;
        this.redisTemplate = redisTemplate;
        this.customTokenService = customTokenService;
        this.jwtTokenUtil = jwtTokenUtil;

        if (redisTemplate == null) {
            log.warn("Redis is not configured. Application will use database-only mode for session management.");
        } else {
            log.info("Redis template initialized successfully for session management.");
        }
    }

    private boolean isRedisAvailable() {
        if (redisTemplate == null) {
            return false;
        }
        try {
            redisTemplate.hasKey("health:check");
            return true;
        } catch (Exception e) {
            log.warn("Redis is not available: {}", e.getMessage());
            return false;
        }
    }

    private void setDeviceFingerprint(String key, String value, long timeout, TimeUnit unit,
                                      String identifier, boolean isSSOUser) {
        if (key == null || value == null || unit == null || identifier == null) {
            log.warn("Null parameters provided to setDeviceFingerprint");
            return;
        }
        boolean redisSuccess = false;

        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, value, timeout, unit);
                redisSuccess = true;
                log.debug("Device fingerprint stored in Redis: {}", key);
            } catch (Exception e) {
                log.warn("Failed to store in Redis, using database fallback: {}", e.getMessage());
            }
        }

        Instant expiresAt = Instant.now().plusSeconds(unit.toSeconds(timeout));

        if (isSSOUser) {
            ssoSessionRepo.findByProfileId(identifier).ifPresent(session -> {
                session.setRedisSessionData(value);
                session.setRedisSessionExpiresAt(expiresAt);
                ssoSessionRepo.save(session);
                log.debug("Device fingerprint stored in database for profileId: {}", identifier);
            });
        } else {
            userRepo.findByEmail(identifier).ifPresent(user -> {
                user.setSessionData(value);
                user.setSessionExpiresAt(expiresAt);
                userRepo.save(user);
                log.debug("Device fingerprint stored in database for email: {}", identifier);
            });
        }

        if (redisSuccess) {
            log.info("Device fingerprint stored in BOTH Redis and Database for: {}", identifier);
        } else {
            log.info("Device fingerprint stored in Database only (Redis unavailable) for: {}", identifier);
        }
    }

    private String getDeviceFingerprint(String key, String identifier, boolean isSSOUser) {
        if (key == null || identifier == null) {
            log.warn("Null parameters provided to getDeviceFingerprint");
            return null;
        }

        if (isRedisAvailable()) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    log.debug("Device fingerprint retrieved from Redis for: {}", identifier);
                    return value;
                }
            } catch (Exception e) {
                log.warn("Failed to read from Redis, using database fallback: {}", e.getMessage());
            }
        }

        if (isSSOUser) {
            return ssoSessionRepo.findByProfileId(identifier)
                .map(session -> {
                    if (session.getRedisSessionExpiresAt() != null &&
                        session.getRedisSessionExpiresAt().isAfter(Instant.now())) {
                        log.debug("Device fingerprint retrieved from Database for profileId: {}", identifier);
                        return session.getRedisSessionData();
                    }
                    return null;
                })
                .orElse(null);
        } else {
            return userRepo.findByEmail(identifier)
                .map(user -> {
                    if (user.getSessionExpiresAt() != null &&
                        user.getSessionExpiresAt().isAfter(Instant.now())) {
                        log.debug("Device fingerprint retrieved from Database for email: {}", identifier);
                        return user.getSessionData();
                    }
                    return null;
                })
                .orElse(null);
        }
    }

    private void deleteDeviceFingerprint(String key, String identifier, boolean isSSOUser) {
        if (key == null || identifier == null) {
            log.warn("Null parameters provided to deleteDeviceFingerprint");
            return;
        }

        if (isRedisAvailable()) {
            try {
                redisTemplate.delete(key);
                log.debug("Device fingerprint deleted from Redis for: {}", identifier);
            } catch (Exception e) {
                log.warn("Failed to delete from Redis: {}", e.getMessage());
            }
        }

        if (isSSOUser) {
            ssoSessionRepo.findByProfileId(identifier).ifPresent(session -> {
                session.setRedisSessionData(null);
                session.setRedisSessionExpiresAt(null);
                ssoSessionRepo.save(session);
                log.debug("Device fingerprint deleted from Database for profileId: {}", identifier);
            });
        } else {
            userRepo.findByEmail(identifier).ifPresent(user -> {
                user.setSessionData(null);
                user.setSessionExpiresAt(null);
                userRepo.save(user);
                log.debug("Device fingerprint deleted from Database for email: {}", identifier);
            });
        }

        log.info("Device fingerprint deleted from all storage locations for: {}", identifier);
    }

    @Transactional
    public void trackSSODevice(String profileId, String deviceFingerprint,
                               String browserFingerprint, HttpServletRequest request) {
        Optional<SSOUserSession> existingSession = ssoSessionRepo.findByProfileId(profileId);
        if (existingSession.isPresent() && Boolean.TRUE.equals(existingSession.get().getIsActive())) {
            log.info("Previous active SSO session detected for profileId: {}", profileId);
        }

        invalidateExistingSSOSessions(profileId);

        String deviceKey = DEVICE_TRACKING_PREFIX + profileId;
        String combinedFingerprint = deviceFingerprint + ":" + browserFingerprint;
        setDeviceFingerprint(deviceKey, combinedFingerprint, 24, TimeUnit.HOURS, profileId, true);

        log.info("Tracked device for SSO user profileId: {}", profileId);
    }

    @Transactional
    public void trackInternalDevice(String email, String deviceFingerprint,
                                   String browserFingerprint, HttpServletRequest request) {
        Optional<InternalUser> existingUser = userRepo.findByEmail(email);
        if (existingUser.isPresent()) {
            log.info("Tracking device for internal user email: {}", email);
        }

        invalidateExistingInternalSessions(email);

        InternalUser user = userRepo.findByEmail(email).orElseThrow();
        user.setLastLoginDevice(deviceFingerprint);
        user.setLastLoginBrowser(browserFingerprint);
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        String deviceKey = DEVICE_TRACKING_PREFIX + email;
        String combinedFingerprint = deviceFingerprint + ":" + browserFingerprint;
        setDeviceFingerprint(deviceKey, combinedFingerprint, 24, TimeUnit.HOURS, email, false);

        log.info("Tracked device for internal user email: {}", email);
    }

    @Transactional
    public void trackInternalDevice(String email, String deviceFingerprint,
                                   String browserFingerprint) {
        invalidateExistingInternalSessions(email);

        InternalUser user = userRepo.findByEmail(email).orElseThrow();
        user.setLastLoginDevice(deviceFingerprint);
        user.setLastLoginBrowser(browserFingerprint);
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        String deviceKey = DEVICE_TRACKING_PREFIX + email;
        String combinedFingerprint = deviceFingerprint + ":" + browserFingerprint;
        setDeviceFingerprint(deviceKey, combinedFingerprint, 24, TimeUnit.HOURS, email, false);

        log.info("Tracked device for internal user email: {}", email);
    }

    private void invalidateExistingSSOSessions(String profileId) {
        String oldToken = getActiveToken(profileId);
        if (oldToken != null) {
            invalidateToken(oldToken);
            log.info("Invalidated old token for profileId: {}", profileId);
        }

        ssoSessionRepo.findByProfileId(profileId).ifPresent(session -> {
            if (Boolean.TRUE.equals(session.getIsActive())) {
                session.setIsActive(false);
                session.setTokenStatus("INVALIDATED");
                ssoSessionRepo.save(session);
                log.info("Invalidated existing SSO session for profileId: {}", profileId);
            }
        });

        String deviceKey = DEVICE_TRACKING_PREFIX + profileId;
        deleteDeviceFingerprint(deviceKey, profileId, true);
    }

    private void invalidateExistingInternalSessions(String email) {
        String oldToken = getActiveToken(email);
        if (oldToken != null) {
            invalidateToken(oldToken);
            log.info("Invalidated old token for email: {}", email);
        }

        String deviceKey = DEVICE_TRACKING_PREFIX + email;
        deleteDeviceFingerprint(deviceKey, email, false);
        log.info("Invalidated existing internal session for email: {}", email);
    }

    public boolean isDeviceValid(String identifier, String deviceFingerprint, String browserFingerprint, boolean isSSOUser) {
        String deviceKey = DEVICE_TRACKING_PREFIX + identifier;
        String storedFingerprint = getDeviceFingerprint(deviceKey, identifier, isSSOUser);

        if (storedFingerprint == null) {
            return false;
        }

        String currentFingerprint = deviceFingerprint + ":" + browserFingerprint;
        return storedFingerprint.equals(currentFingerprint);
    }

    public void invalidateDevice(String identifier, boolean isSSOUser) {
        String deviceKey = DEVICE_TRACKING_PREFIX + identifier;
        deleteDeviceFingerprint(deviceKey, identifier, isSSOUser);
        log.info("Device invalidated for identifier: {}", identifier);
    }

    public boolean validateSSOSession(String accessToken) {
        try {

            JWTClaimsSet claims = customTokenService.validateAndParseToken(accessToken);
            String profileId = claims.getSubject();

            if (isTokenInvalidated(accessToken)) {
                log.warn("❌ Token has been explicitly blacklisted for profileId: {}", profileId);
                return false;
            }

            Optional<SSOUserSession> sessionOpt = ssoSessionRepo.findByProfileId(profileId);

            if (sessionOpt.isEmpty()) {

                return true;
            }

            SSOUserSession session = sessionOpt.get();

            if (!Boolean.TRUE.equals(session.getIsActive())) {
                return false;
            }

            if (!"ACTIVE".equals(session.getTokenStatus())) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateInternalSession(String accessToken) {
        try {
            if (!jwtTokenUtil.isTokenValid(accessToken) || !jwtTokenUtil.isAccessToken(accessToken)) {
                log.warn("Invalid or non-access token provided");
                return false;
            }

            String email = jwtTokenUtil.extractEmail(accessToken);

            if (isTokenInvalidated(accessToken)) {
                log.warn("Token has been explicitly invalidated for email: {}", email);
                return false;
            }

            Optional<InternalUser> userOpt = userRepo.findByEmail(email);
            if (userOpt.isEmpty()) {
                log.warn("No user found for email: {}", email);
                return false;
            }

            InternalUser user = userOpt.get();
            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                log.warn("User account is deleted for email: {}", email);
                return false;
            }

            log.debug("Internal session validated successfully for email: {}", email);
            return true;

        } catch (Exception e) {
            log.error("Internal session validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    public void invalidateSSOSession(String accessToken) {
        try {
            JWTClaimsSet claims = customTokenService.validateAndParseToken(accessToken);
            String profileId = claims.getSubject();

            ssoSessionRepo.findByProfileId(profileId).ifPresent(session -> {
                session.setIsActive(false);
                session.setTokenStatus("INVALIDATED");
                ssoSessionRepo.save(session);
                log.info("Session invalidated for profileId: {}", profileId);
            });

            invalidateToken(accessToken);

            String deviceKey = DEVICE_TRACKING_PREFIX + profileId;
            deleteDeviceFingerprint(deviceKey, profileId, true);

        } catch (Exception e) {
            log.error("Failed to invalidate SSO session: {}", e.getMessage());
        }
    }

    public void invalidateInternalSession(String accessToken) {
        try {
            String email = jwtTokenUtil.extractEmail(accessToken);

            invalidateToken(accessToken);

            String deviceKey = DEVICE_TRACKING_PREFIX + email;
            deleteDeviceFingerprint(deviceKey, email, false);

            log.info("Session invalidated for email: {}", email);

        } catch (Exception e) {
            log.error("Failed to invalidate internal session: {}", e.getMessage());
        }
    }

    private void invalidateToken(String token) {
        String key = INVALIDATED_TOKEN_PREFIX + token;
        boolean storedInRedis = false;

        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, "true", 24, TimeUnit.HOURS);
                storedInRedis = true;
                log.debug("Token added to invalidation list in Redis");
            } catch (Exception e) {
                log.warn("Failed to store invalidated token in Redis: {}", e.getMessage());
            }
        }

        if (!storedInRedis) {
            log.warn("Token invalidation stored in memory only - may not persist across restarts");
        }
    }

    private boolean isTokenInvalidated(String token) {
        String key = INVALIDATED_TOKEN_PREFIX + token;

        if (isRedisAvailable()) {
            try {
                Boolean exists = redisTemplate.hasKey(key);
                return Boolean.TRUE.equals(exists);
            } catch (Exception e) {
                log.warn("Failed to check token invalidation in Redis: {}", e.getMessage());
            }
        }

        return false;
    }

    private static final String USER_TOKEN_PREFIX = "user_token:";

    private void storeActiveToken(String identifier, String token, boolean isSSOUser) {
        if (identifier == null || token == null) {
            log.warn("Cannot store active token: identifier or token is null");
            return;
        }

        String key = USER_TOKEN_PREFIX + identifier;

        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, token, 24, TimeUnit.HOURS);
                log.debug("Active token stored in Redis for user: {}", identifier);
            } catch (Exception e) {
                log.warn("Failed to store active token in Redis: {}", e.getMessage());
            }
        }
    }

    private String getActiveToken(String identifier) {
        String key = USER_TOKEN_PREFIX + identifier;

        if (isRedisAvailable()) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("Failed to get active token from Redis: {}", e.getMessage());
            }
        }
        return null;
    }

    public void storeUserActiveToken(String identifier, String token, boolean isSSOUser) {
        storeActiveToken(identifier, token, isSSOUser);
    }

    public void invalidateTokenOnly(String accessToken) {
        try {

            invalidateToken(accessToken);
            log.info("Token invalidated (blacklisted) without affecting session");
        } catch (Exception e) {
            log.error("Failed to invalidate token: {}", e.getMessage());
        }
    }
}
