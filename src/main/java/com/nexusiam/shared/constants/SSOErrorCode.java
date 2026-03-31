package com.nexusiam.shared.constants;

import com.nexusiam.presentation.exception.base.ExchangeError;
import org.springframework.http.HttpStatus;

public enum SSOErrorCode implements ExchangeError {

    INVALID_AUTH_CODE("SSO_001", "Invalid or expired authorization code", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("SSO_002", "Invalid credentials provided", HttpStatus.UNAUTHORIZED),
    PKCE_VALIDATION_FAILED("SSO_003", "PKCE validation failed", HttpStatus.UNAUTHORIZED),
    TOKEN_EXCHANGE_FAILED("SSO_004", "Token exchange with SSO provider failed", HttpStatus.BAD_GATEWAY),
    PROFILE_FETCH_FAILED("SSO_005", "Failed to fetch user profile from SSO", HttpStatus.BAD_GATEWAY),
    INVALID_PROFILE_DATA("SSO_006", "Invalid or incomplete profile data", HttpStatus.BAD_REQUEST),
    SESSION_NOT_FOUND("SSO_007", "User session not found", HttpStatus.NOT_FOUND),
    SESSION_EXPIRED("SSO_008", "User session has expired", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID("SSO_009", "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED_ACCESS("SSO_010", "Unauthorized access attempt", HttpStatus.FORBIDDEN),
    CONFIGURATION_ERROR("SSO_011", "SSO configuration error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_TOKEN("SSO_012", "Invalid token provided", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("SSO_013", "Token has expired", HttpStatus.UNAUTHORIZED),
    MISSING_REQUIRED_FIELD("SSO_014", "Required field is missing", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("SSO_015", "Invalid request parameters", HttpStatus.BAD_REQUEST),
    SSO_PROVIDER_ERROR("SSO_016", "SSO provider returned an error", HttpStatus.BAD_GATEWAY),
    NETWORK_ERROR("SSO_017", "Network error communicating with SSO provider", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_STATE("SSO_018", "Invalid state parameter", HttpStatus.BAD_REQUEST),
    CALLBACK_ERROR("SSO_019", "Error processing SSO callback", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_FOUND("SSO_020", "User not found in system", HttpStatus.NOT_FOUND),
    PROFILE_UPDATE_FAILED("SSO_021", "Failed to update user profile", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REGISTRATION("SSO_022", "Invalid registration data", HttpStatus.BAD_REQUEST),
    REGISTRATION_NOT_FOUND("SSO_023", "Registration not found", HttpStatus.NOT_FOUND),
    MULTIPLE_REGISTRATIONS("SSO_024", "Multiple registrations found", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND("SSO_025", "Role not found for user", HttpStatus.NOT_FOUND),
    ROLE_SWITCH_FAILED("SSO_026", "Failed to switch role", HttpStatus.BAD_REQUEST),
    INVALID_UNIT_ID("SSO_027", "Invalid unit ID", HttpStatus.BAD_REQUEST),
    UNIT_NOT_FOUND("SSO_028", "Unit not found", HttpStatus.NOT_FOUND),
    KYC_NOT_FOUND("SSO_029", "KYC information not found", HttpStatus.NOT_FOUND),
    KYC_ALREADY_EXISTS("SSO_030", "KYC information already exists", HttpStatus.CONFLICT),
    KYC_UPDATE_FAILED("SSO_031", "Failed to update KYC information", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KYC_DATA("SSO_032", "Invalid KYC data provided", HttpStatus.BAD_REQUEST),
    QUARTERLY_LOCK_ACTIVE("SSO_033", "KYC is locked for quarterly period", HttpStatus.FORBIDDEN),
    USER_GROUP_NOT_FOUND("SSO_034", "User group not found", HttpStatus.NOT_FOUND),
    INVALID_PROFILE_ID("SSO_035", "Invalid profile ID", HttpStatus.BAD_REQUEST),
    PROFILE_NOT_FOUND("SSO_036", "Profile not found", HttpStatus.NOT_FOUND),
    ACCESS_DENIED("SSO_037", "Access denied", HttpStatus.FORBIDDEN),
    INSUFFICIENT_PERMISSIONS("SSO_038", "Insufficient permissions", HttpStatus.FORBIDDEN),
    ACCOUNT_INACTIVE("SSO_039", "User account is inactive", HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED("SSO_040", "User account is locked", HttpStatus.FORBIDDEN),
    INVALID_TOKEN_TYPE("SSO_041", "Invalid token type", HttpStatus.BAD_REQUEST),
    TOKEN_REVOKED("SSO_042", "Token has been revoked", HttpStatus.UNAUTHORIZED),
    CONCURRENT_MODIFICATION("SSO_043", "Concurrent modification detected", HttpStatus.CONFLICT),
    RESOURCE_CONFLICT("SSO_044", "Resource conflict", HttpStatus.CONFLICT),
    VALIDATION_FAILED("SSO_045", "Validation failed", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("SSO_046", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE("SSO_047", "Service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    TIMEOUT("SSO_048", "Request timeout", HttpStatus.REQUEST_TIMEOUT),
    RATE_LIMIT_EXCEEDED("SSO_049", "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
    UNSUPPORTED_OPERATION("SSO_050", "Operation not supported", HttpStatus.NOT_IMPLEMENTED),
    ERR_EXCHANGE_USER_MGMNT_007("ERR_EXCHANGE_USER_MGMNT_007", "Token refresh operation failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String errorMsg;
    private final HttpStatus status;

    SSOErrorCode(String errorCode, String errorMsg, HttpStatus status) {
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.status = status;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMsg() {
        return errorMsg;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
