package com.nexusiam.application.service.factory;

import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.core.domain.entity.SSOUserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class SSOSessionFactory {

    public SSOUserSession createTemporarySession(String state, String pkceVerifier, String pkceChallenge) {
        log.debug("Creating temporary session for state: {}", state);

        String profileId = SSOConstants.TEMP_SESSION_PREFIX +
                          (state != null ? state : java.util.UUID.randomUUID().toString());

        return SSOUserSession.builder()
                .profileId(profileId)
                .authCode(state != null ? state : "")
                .authCodeExpiresAt(Instant.now().plusSeconds(SSOConstants.PKCE_EXPIRY_SECONDS))
                .pkceVerifier(pkceVerifier)
                .pkceVerifierExpiresAt(Instant.now().plusSeconds(SSOConstants.PKCE_EXPIRY_SECONDS))
                .pkceChallenge(pkceChallenge)
                .pkceMethod(SSOConstants.PKCE_CHALLENGE_METHOD)
                .isActive(false)
                .tokenStatus(SSOConstants.TOKEN_STATUS_PENDING)
                .build();
    }

    public SSOUserSession createActiveSession(String profileId) {
        log.debug("Creating active session for profileId: {}", profileId);

        return SSOUserSession.builder()
                .profileId(profileId)
                .isActive(false)
                .tokenStatus(SSOConstants.TOKEN_STATUS_PENDING)
                .createdDate(Instant.now())
                .build();
    }

    public SSOUserSession createFromExisting(SSOUserSession existing) {
        if (existing == null) {
            throw new IllegalArgumentException("Existing session cannot be null");
        }

        log.debug("Creating session from existing for profileId: {}", existing.getProfileId());
        return existing;
    }
}
