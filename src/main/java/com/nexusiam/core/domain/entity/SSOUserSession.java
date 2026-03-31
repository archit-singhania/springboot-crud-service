package com.nexusiam.core.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sso_user_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SSOUserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, unique = true)
    private String profileId;

    @Column(name = "auth_code", nullable = false)
    private String authCode;

    @Column(name = "auth_code_expires_at", nullable = false)
    private Instant authCodeExpiresAt;

    @Column(name = "pkce_challenge")
    private String pkceChallenge;

    @Column(name = "pkce_method")
    private String pkceMethod;

    @Column(name = "pkce_verifier", columnDefinition = "TEXT")
    private String pkceVerifier;

    @Column(name = "pkce_verifier_expires_at")
    private Instant pkceVerifierExpiresAt;

    @Column(name = "sso_access_token", columnDefinition = "TEXT")
    private String ssoAccessToken;

    @Column(name = "sso_refresh_token", columnDefinition = "TEXT")
    private String ssoRefreshToken;

    @Column(name = "sso_id_token", columnDefinition = "TEXT")
    private String ssoIdToken;

    @Column(name = "sso_token_issued_at")
    private Instant ssoTokenIssuedAt;

    @Column(name = "sso_token_expires_at")
    private Instant ssoTokenExpiresAt;

    @Column(name = "token_type")
    private String tokenType;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    @Column(name = "jti")
    private String jti;

    @Column(name = "token_status")
    private String tokenStatus;

    @Column(name = "last_login_on")
    private Instant lastLoginOn;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "unit_id")
    private String unitId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_metadata", columnDefinition = "jsonb")
    private JsonNode sessionMetadata;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "browser_fingerprint")
    private String browserFingerprint;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "redis_session_data", columnDefinition = "TEXT")
    private String redisSessionData;

    @Column(name = "redis_session_expires_at")
    private Instant redisSessionExpiresAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdDate == null) {
            createdDate = now;
        }
        if (updatedDate == null) {
            updatedDate = now;
        }
        if (tokenStatus == null) {
            tokenStatus = "ACTIVE";
        }
        if (tokenType == null) {
            tokenType = "Bearer";
        }

        if (pkceMethod == null && pkceChallenge != null) {
            pkceMethod = "S256";
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = Instant.now();
    }
}
