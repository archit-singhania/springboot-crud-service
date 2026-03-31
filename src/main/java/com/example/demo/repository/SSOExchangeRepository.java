package com.example.demo.repository;

import com.example.demo.model.SSOExchangeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface SSOExchangeRepository extends JpaRepository<SSOExchangeMaster, Long> {

    Optional<SSOExchangeMaster> findByProfileId(String profileId);

    Optional<SSOExchangeMaster> findByOrgId(String orgId);

    Optional<SSOExchangeMaster> findByRefreshToken(String refreshToken);

    Optional<SSOExchangeMaster> findByCustomAccessToken(String customAccessToken);

    Optional<SSOExchangeMaster> findByCustomRefreshToken(String customRefreshToken);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
            "FROM SSOExchangeMaster s " +
            "WHERE s.orgId = :orgId " +
            "AND s.status = 'Active' " +
            "AND s.exchangeAccess = 'allowed' " +
            "AND s.complianceStatus = 'active' " +
            "AND (s.validTill IS NULL OR s.validTill >= :currentDate)")
    boolean isOrganizationActiveForTrading(@Param("orgId") String orgId,
                                           @Param("currentDate") Instant currentDate);

    @Modifying
    @Transactional
    @Query("UPDATE SSOExchangeMaster s " +
            "SET s.customAccessToken = NULL, " +
            "s.customRefreshToken = NULL, " +
            "s.tokenStatus = 'REVOKED', " +
            "s.updatedAt = :now " +
            "WHERE s.profileId = :profileId")
    void revokeCustomTokensByProfileId(@Param("profileId") String profileId,
                                       @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE SSOExchangeMaster s " +
            "SET s.tokenStatus = 'EXPIRED', " +
            "s.updatedAt = :now " +
            "WHERE s.expiresAt < :now " +
            "AND s.tokenStatus = 'ACTIVE'")
    int markExpiredTokens(@Param("now") Instant now);
}