package com.nexusiam.application.service.session;

import com.nexusiam.application.service.factory.SSOSessionFactory;
import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.core.domain.entity.SSOUserSession;
import com.nexusiam.core.domain.repository.SSOUserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionLifecycleService {

    private final SSOUserSessionRepository sessionRepo;

    public void handleConcurrentSessions(String profileId) {
        List<SSOUserSession> existingSessions = sessionRepo.findAllByProfileId(profileId);

        if (existingSessions.size() > SSOConstants.MAX_CONCURRENT_SESSIONS) {
            log.warn("Found {} sessions for profileId: {}. Deactivating old sessions.",
                existingSessions.size(), profileId);

            deactivateOldSessions(existingSessions);
        }
    }

    public SSOUserSession getOrCreateSession(String profileId, SSOSessionFactory sessionFactory) {
        log.debug("Looking for existing sessions for profileId: {}", profileId);
        List<SSOUserSession> existingSessions = sessionRepo.findAllByProfileId(profileId);

        if (existingSessions.isEmpty()) {
            log.info("No existing session found. Creating new session for profileId: {}", profileId);
            SSOUserSession newSession = sessionFactory.createActiveSession(profileId);
            log.info("New session created (unsaved) for profileId: {}", profileId);
            return newSession;
        }

        log.debug("Reusing existing session ID: {} for profileId: {}",
            existingSessions.get(0).getId(), profileId);
        return existingSessions.get(0);
    }

    private void deactivateOldSessions(List<SSOUserSession> sessions) {

        for (int i = 1; i < sessions.size(); i++) {
            SSOUserSession oldSession = sessions.get(i);
            oldSession.setIsActive(false);
            sessionRepo.save(oldSession);
            log.debug("Deactivated old session for profileId: {}", oldSession.getProfileId());
        }
    }
}
