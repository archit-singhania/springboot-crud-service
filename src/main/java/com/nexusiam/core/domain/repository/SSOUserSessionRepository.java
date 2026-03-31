package com.nexusiam.core.domain.repository;

import com.nexusiam.core.domain.entity.SSOUserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SSOUserSessionRepository extends JpaRepository<SSOUserSession, Long> {

    Optional<SSOUserSession> findByProfileId(String profileId);

    Optional<SSOUserSession> findByAuthCode(String authCode);

    Optional<SSOUserSession> findBySsoAccessToken(String token);

    Optional<SSOUserSession> findBySsoRefreshToken(String token);

    List<SSOUserSession> findByTokenStatus(String status);

    List<SSOUserSession> findBySsoTokenExpiresAtBeforeAndTokenStatus(Instant expiresAt, String status);

    @Query("SELECT s FROM SSOUserSession s " +
           "WHERE s.tokenStatus = 'ACTIVE' " +
           "ORDER BY s.lastLoginOn DESC")
    List<SSOUserSession> findAllActiveSessions();

    @Modifying
    @Transactional
    @Query("UPDATE SSOUserSession s " +
           "SET s.ssoAccessToken = NULL, " +
           "s.ssoRefreshToken = NULL, " +
           "s.tokenStatus = 'REVOKED', " +
           "s.updatedDate = :now " +
           "WHERE s.profileId = :profileId")
    void revokeTokensByProfileId(@Param("profileId") String profileId,
                                  @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE SSOUserSession s " +
           "SET s.tokenStatus = 'EXPIRED', " +
           "s.updatedDate = :now " +
           "WHERE s.ssoTokenExpiresAt < :now " +
           "AND s.tokenStatus = 'ACTIVE'")
    int markExpiredTokens(@Param("now") Instant now);

    @Query("SELECT COUNT(s) FROM SSOUserSession s WHERE s.tokenStatus = 'ACTIVE'")
    long countActiveSessions();

    @Query("SELECT s FROM SSOUserSession s WHERE s.profileId = :profileId ORDER BY s.lastActivityAt DESC")
    List<SSOUserSession> findAllByProfileId(@Param("profileId") String profileId);

    @Query("SELECT s FROM SSOUserSession s WHERE s.profileId = :profileId AND s.isActive = true ORDER BY s.lastActivityAt DESC")
    Optional<SSOUserSession> findActiveByProfileId(@Param("profileId") String profileId);
}
