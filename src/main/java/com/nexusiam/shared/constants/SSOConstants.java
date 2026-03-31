package com.nexusiam.shared.constants;

public final class SSOConstants {
    private SSOConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static final String PKCE_PREFIX = "pkce:";
    public static final int PKCE_VERIFIER_LENGTH = 32;
    public static final int PKCE_EXPIRY_SECONDS = 300;
    public static final String PKCE_CHALLENGE_METHOD = "S256";
    public static final String TEMP_SESSION_PREFIX = "TEMP_";

    public static final String TOKEN_TYPE_BEARER = "Bearer";
    public static final String TOKEN_STATUS_PENDING = "PENDING";
    public static final String TOKEN_STATUS_ACTIVE = "ACTIVE";
    public static final int DEFAULT_TOKEN_EXPIRY_SECONDS = 900;
    public static final int ACCESS_TOKEN_EXPIRY_SECONDS = 9000;
    public static final int REFRESH_TOKEN_EXPIRY_SECONDS = 86400;
    public static final int AUTH_CODE_EXPIRY_SECONDS = 60;

    public static final String GRANT_TYPE_AUTH_CODE = "authorization_code";
    public static final String GRANT_TYPE_REFRESH = "refresh_token";
    public static final String RESPONSE_TYPE_CODE = "code";

    public static final String HEALTH_CHECK_KEY = "health:check";
    public static final int MAX_CONCURRENT_SESSIONS = 1;

    public static final int MAX_REGISTRATIONS_PER_USER = 50;
    public static final int MAX_AUTHORIZED_CATEGORIES = 50;

    public static final String DEFAULT_STATUS = "active";
    public static final String DEFAULT_COMPLIANCE_STATUS = "active";
    public static final String DEFAULT_EXCHANGE_ACCESS = "allowed";
    public static final String DEFAULT_DESIGNATION = "Authorized Signatory";
    public static final long DEFAULT_VALID_TILL_DAYS = 365L;

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_SUSPENDED = "suspended";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String COMPLIANCE_STATUS_ACTIVE = "active";
    public static final String COMPLIANCE_STATUS_UNDER_REVIEW = "under-review";
    public static final String COMPLIANCE_STATUS_REVOKED = "revoked";
    public static final String COMPLIANCE_STATUS_SUSPENDED = "suspended";
    public static final String EXCHANGE_ACCESS_ALLOWED = "allowed";
    public static final String EXCHANGE_ACCESS_DENIED = "denied";

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_REQUEST_HASH = "X-Request-Hash";
    public static final String HEADER_AUTHORIZATION = "Authorization";
}
