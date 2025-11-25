package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sso_exchange_master")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SSOExchangeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_category")
    private String userCategory;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "profile_id", nullable = false)
    private String profileId;

    @Column(name = "auth_code")
    private String authCode;

    @Column(name = "auth_code_expires_at")
    private Instant authCodeExpiresAt;

    @Column(name = "access_token", length = 2048)
    private String accessToken;

    @Column(name = "refresh_token", length = 2048)
    private String refreshToken;

    @Column(name = "token_type")
    private String tokenType;

    @Column(name = "expires_in_seconds")
    private Integer expiresInSeconds;

    @Column(name = "id_token", length = 2048)
    private String idToken;

    @Column(name = "okta_id")
    private String oktaId;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_address", length = 500)
    private String companyAddress;

    @Column(name = "state")
    private String state;

    @Column(name = "pincode")
    private String pincode;

    @Column(name = "gst_number")
    private String gstNumber;

    @Column(name = "cin_number")
    private String cinNumber;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "authorized_person_name")
    private String authorizedPersonName;

    @Column(name = "designation")
    private String designation;

    @Column(name = "email")
    private String email;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "landline")
    private String landline;

    @Column(name = "status")
    private String status;

    @Column(name = "compliance_status")
    private String complianceStatus;

    @Column(name = "exchange_access")
    private String exchangeAccess;

    @Column(name = "valid_till")
    private Instant validTill;

    @Column(name = "last_login_on")
    private Instant lastLoginOn;

    @Column(name = "portal_id")
    private String portalId;

    @Column(name = "portal_name")
    private String portalName;

    @Column(name = "party_id")
    private String partyId;

    @Column(name = "portal_role")
    private String portalRole;

    @Column(name = "type_id")
    private String typeId;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "registration_status")
    private String registrationStatus;

    @Column(name = "registration_valid_till")
    private java.time.LocalDate registrationValidTill;

    @Column(name = "portal_internal_id")
    private String portalInternalId;

    @Column(name = "unit_address", columnDefinition = "TEXT")
    private String unitAddress;

    @Column(name = "unit_state")
    private String unitState;

    @Column(name = "unit_pincode")
    private String unitPincode;

    @Column(name = "unit_gst_number")
    private String unitGstNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authorized_categories", columnDefinition = "jsonb")
    private String authorizedCategories;

    @Column(name = "certificate_count")
    private Integer certificateCount;

    @Column(name = "market_role")
    private String marketRole;

    @Column(name = "is_trade_allowed")
    private Boolean isTradeAllowed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb")
    private String permissions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resources", columnDefinition = "jsonb")
    private String resources;

    @Column(name = "raw_sso_payload", columnDefinition = "TEXT")
    private String rawSsoPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_registration_block", columnDefinition = "jsonb")
    private String rawRegistrationBlock;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "sync_status")
    private String syncStatus;

    @Column(name = "sync_error_message", columnDefinition = "TEXT")
    private String syncErrorMessage;

    @Column(name = "custom_access_token", length = 2048)
    private String customAccessToken;

    @Column(name = "custom_refresh_token", length = 2048)
    private String customRefreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "jti")
    private String jti;

    @Column(name = "registrations", columnDefinition = "TEXT")
    private String registrations;

    @Column(name = "scope")
    private String scope;

    @Column(name = "service_token", length = 2048)
    private String serviceToken;

    @Column(name = "service_token_expires_at")
    private Instant serviceTokenExpiresAt;

    @Column(name = "service_token_issued_at")
    private Instant serviceTokenIssuedAt;

    @Column(name = "token_status")
    private String tokenStatus;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
