package com.nexusiam.application.service.authentication;

import com.nexusiam.application.service.factory.SSOSessionFactory;
import com.nexusiam.application.service.mapper.SessionDataMapper;
import com.nexusiam.application.service.mapper.UserGroupDataMapper;
import com.nexusiam.application.service.orchestrator.UserGroupOrchestrator;
import com.nexusiam.application.service.session.PKCEService;
import com.nexusiam.application.service.session.SessionLifecycleService;
import com.nexusiam.application.service.session.SessionManagementService;
import com.nexusiam.application.service.strategy.TokenExchangeStrategy;
import com.nexusiam.application.service.token.CustomTokenService;
import com.nexusiam.application.service.token.JwksService;
import com.nexusiam.application.service.utility.DeviceFingerprintService;
import com.nexusiam.application.service.utility.IdGeneratorService;
import com.nexusiam.application.service.validation.AuthCodeValidator;
import com.nexusiam.application.service.validation.SSOSessionValidator;
import com.nexusiam.infrastructure.config.oauth.OktaOAuth2Config;
import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.shared.constants.SSOErrorCode;
import com.nexusiam.application.dto.response.LoginResponse;
import com.nexusiam.application.dto.response.SSOProfileResponse;
import com.nexusiam.presentation.exception.SSOAuthenticationException;
import com.nexusiam.presentation.exception.SSOSessionException;
import com.nexusiam.presentation.exception.SSOTokenExchangeException;
import com.nexusiam.core.domain.entity.SSOUserGroup;
import com.nexusiam.core.domain.entity.SSOUserSession;
import com.nexusiam.core.domain.repository.SSOUserGroupRepository;
import com.nexusiam.core.domain.repository.SSOUserSessionRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SSOAuthenticationService implements SSOExchangeService {

    private final SSOUserGroupRepository userGroupRepo;
    private final SSOUserSessionRepository sessionRepo;
    private final CustomTokenService customTokenService;
    private final JwksService jwksService;
    private final SessionManagementService sessionManagementService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final OktaOAuth2Config oktaConfig;
    private final IdGeneratorService idGeneratorService;

    private final AuthCodeValidator authCodeValidator;
    private final SSOSessionValidator ssoSessionValidator;

    private final TokenExchangeStrategy tokenExchangeStrategy;

    private final SSOProfileFetchService ssoProfileFetchService;

    private final SessionDataMapper sessionDataMapper;
    private final UserGroupDataMapper userGroupDataMapper;

    private final SSOSessionFactory sessionFactory;

    private final UserGroupOrchestrator userGroupOrchestrator;
    private final SessionLifecycleService sessionLifecycleService;

    private final PKCEService pkceService;

    @Override
    public Map<String, String> requestAuthCode(String state) {
        log.info("Initiating SSO authorization request for state: {}", state);

        String codeVerifier = pkceService.generateCodeVerifier();
        String codeChallenge = pkceService.generateCodeChallenge(codeVerifier);

        boolean stored = pkceService.storePkceVerifier(
            state,
            codeVerifier,
            codeChallenge,
            () -> createTemporarySession(state, codeVerifier, codeChallenge)
        );

        if (!stored) {
            log.error("PKCE storage failed in both Redis and database for state: {}", state);
            throw new SSOSessionException(SSOErrorCode.CONFIGURATION_ERROR);
        }

        String authUrl = buildAuthorizationUrl(state, codeChallenge);

        log.info("Authorization URL generated successfully with PKCE stored in both storages for state: {}", state);
        return Map.of(
                "authorizationUrl", authUrl,
                "state", state,
                "message", "Redirect user to authorizationUrl to login via Okta"
        );
    }

    @Override
    @Transactional
    public LoginResponse exchangeAuthCode(String authCode, String state, String ipAddress,
                                        String userAgent, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("[RequestID: {}] Starting token exchange for state: {}", requestId, state);

        try {

            String codeVerifier = authCodeValidator.retrieveAndValidateCodeVerifier(state);

            Map<String, Object> oktaTokenResponse = exchangeTokensWithProvider(authCode, codeVerifier, requestId);

            TokenData tokenData = extractAndValidateTokens(oktaTokenResponse, requestId);

            SSOProfileResponse profileResponse = fetchAndEnrichProfile(
                tokenData.accessToken,
                tokenData.idClaims,
                requestId
            );

            SSOUserGroup userGroup = userGroupOrchestrator.getOrCreateAndUpdateUserGroup(
                profileResponse.getProfileId(),
                profileResponse.getGrpId(),
                profileResponse
            );

            sessionLifecycleService.handleConcurrentSessions(profileResponse.getProfileId());
            SSOUserSession session = sessionLifecycleService.getOrCreateSession(
                profileResponse.getProfileId(),
                sessionFactory
            );

            updateSessionWithDeviceAndTokens(session, tokenData, authCode, ipAddress, userAgent, request);

            Map<String, String> customTokens = generateCustomTokens(userGroup, profileResponse);

            updateSessionWithCustomTokens(session, customTokens);
            log.info("[RequestID: {}] Session saved for profileId: {} with id: {}",
                requestId, profileResponse.getProfileId(), session.getId());

            verifySessionPersistence(profileResponse.getProfileId(), requestId);

            authCodeValidator.cleanupPkceVerifier(state);

            log.info("[RequestID: {}] Token exchange completed successfully for profileId: {}",
                requestId, profileResponse.getProfileId());

            return buildLoginResponse(customTokens, tokenData.idToken, profileResponse);

        } catch (SSOAuthenticationException | SSOTokenExchangeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RequestID: {}] Token exchange failed", requestId, e);
            throw new SSOTokenExchangeException("Token exchange failed", e);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> getProfile(String customAccessToken) {
        log.debug("Fetching profile for custom access token");

        JWTClaimsSet claims;
        try {
            claims = customTokenService.validateAndParseToken(customAccessToken);
        } catch (Exception e) {
            log.error("Custom token validation failed", e);
            throw new SSOAuthenticationException(SSOErrorCode.INVALID_CREDENTIALS);
        }

        String profileId = claims.getSubject();
        SSOUserGroup userGroup = userGroupRepo.findByProfileId(profileId)
                .orElseThrow(() -> new SSOAuthenticationException(SSOErrorCode.SESSION_EXPIRED));

        return userGroup.toProfileMap(userGroupDataMapper, profileId);
    }

    @Override
    @Transactional
    public LoginResponse refreshAccessToken(String customRefreshToken, String ipAddress, String userAgent) {
        String requestId = UUID.randomUUID().toString();
        log.info("[RequestID: {}] Starting token refresh", requestId);

        try {

            JWTClaimsSet claims = validateRefreshToken(customRefreshToken, requestId);
            String profileId = claims.getSubject();
            String grpId = extractGrpId(claims);

            SSOUserSession session = retrieveAndValidateSession(profileId, requestId);

            if (ssoSessionValidator.isOktaTokenExpired(session)) {
                refreshSSOTokens(session, requestId);
            }

            SSOProfileResponse profileResponse = ssoProfileFetchService.fetchProfile(
                session.getSsoAccessToken(),
                requestId
            );

            String exchangeProfileId = profileId;
            String exchangeGrpId = grpId;

            log.debug("[RequestID: {}] Using Exchange IDs: profileId={}, grpId={}",
                requestId, exchangeProfileId, exchangeGrpId);

            SSOUserGroup userGroup = userGroupOrchestrator.getOrCreateAndUpdateUserGroup(
                profileId,
                grpId,
                profileResponse
            );

            Map<String, String> customTokens = generateCustomTokensWithExchangeIds(
                userGroup,
                exchangeProfileId,
                exchangeGrpId
            );

            session.setIsActive(true);
            session.setTokenStatus("ACTIVE");
            session.setLastActivityAt(Instant.now());
            sessionManagementService.storeUserActiveToken(profileId, customTokens.get("accessToken"), true);
            updateSessionWithCustomTokens(session, customTokens);

            log.info("[RequestID: {}] Session reactivated for profileId: {}", requestId, profileId);

            log.info("[RequestID: {}] Token refresh completed for profileId: {}", requestId, profileId);

            return buildRefreshResponse(customTokens, session.getSsoIdToken(), profileId, grpId);

        } catch (SSOAuthenticationException | SSOSessionException | SSOTokenExchangeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RequestID: {}] Token refresh failed", requestId, e);
            throw new SSOTokenExchangeException(
                "Token refresh failed for requestId: " + requestId,
                e
            );
        }
    }

    @Override
    public Map<String, Object> introspectToken(String token) {
        try {
            JWTClaimsSet claims = customTokenService.validateAndParseToken(token);

            String profileId = claims.getSubject();
            SSOUserSession session = sessionRepo.findByProfileId(profileId).orElse(null);

            Map<String, Object> result = new HashMap<>();
            result.put("active", true);
            result.put("profile_id", profileId);
            result.put("grp_id", claims.getClaim("grp_id"));
            result.put("token_type", claims.getClaim("token_type"));
            result.put("exp", claims.getExpirationTime().toInstant().getEpochSecond());
            result.put("iat", claims.getIssueTime().toInstant().getEpochSecond());

            if (session != null) {
                result.put("token_status", session.getTokenStatus());
            }

            return result;
        } catch (Exception e) {
            log.debug("Token introspection failed: {}", e.getMessage());
            return Map.of("active", false);
        }
    }

    private SSOUserSession createTemporarySession(String state, String codeVerifier, String codeChallenge) {
        SSOUserSession tempSession = sessionFactory.createTemporarySession(state, codeVerifier, codeChallenge);

        ensureTempProfileExists(tempSession.getProfileId());

        log.info("Temporary session created for state: {}", state);
        return tempSession;
    }

    private void ensureTempProfileExists(String tempProfileId) {
        if (!userGroupRepo.existsByProfileId(tempProfileId)) {
            log.debug("Creating temporary profile for: {}", tempProfileId);

            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode emptyJsonObject;
            try {
                emptyJsonObject = objectMapper.readTree("{}");
            } catch (Exception e) {
                log.error("Failed to create empty JSON object", e);
                emptyJsonObject = objectMapper.createObjectNode();
            }

            SSOUserGroup tempProfile = SSOUserGroup.builder()
                    .profileId(tempProfileId)
                    .grpId("TEMP_" + UUID.randomUUID())
                    .status("TEMPORARY")
                    .userTypeId((short) 1)
                    .registrations(emptyJsonObject)
                    .companyName("Temporary Session")
                    .email("temp@session.local")
                    .createdDate(Instant.now())
                    .updatedDate(Instant.now())
                    .build();

            try {
                userGroupRepo.save(Objects.requireNonNull(tempProfile));
                log.info("Temporary profile created: {}", tempProfileId);
            } catch (Exception e) {
                log.error("Failed to create temporary profile: {}", tempProfileId, e);
                throw new SSOSessionException(SSOErrorCode.CONFIGURATION_ERROR);
            }
        }
    }

    private String buildAuthorizationUrl(String state, String codeChallenge) {
        return UriComponentsBuilder.fromUriString(
                Objects.requireNonNull(oktaConfig.getAuthorizationEndpoint(),
                    "Authorization endpoint must not be null"))
                .queryParam("response_type", SSOConstants.RESPONSE_TYPE_CODE)
                .queryParam("client_id", oktaConfig.getClientId())
                .queryParam("redirect_uri", oktaConfig.getRedirectUri())
                .queryParam("scope", oktaConfig.getScope())
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", SSOConstants.PKCE_CHALLENGE_METHOD)
                .build()
                .toUriString();
    }

    private Map<String, Object> exchangeTokensWithProvider(String authCode, String codeVerifier, String requestId) {
        log.debug("[RequestID: {}] Exchanging auth code with SSO provider", requestId);
        return tokenExchangeStrategy.exchangeAuthCodeForTokens(authCode, codeVerifier, requestId);
    }

    private TokenData extractAndValidateTokens(Map<String, Object> tokenResponse, String requestId) {
        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");
        String idToken = (String) tokenResponse.get("id_token");
        Integer expiresIn = sessionDataMapper.extractExpiresIn(tokenResponse);

        JWTClaimsSet idClaims = jwksService.validateAndParseToken(idToken);
        String jti = idClaims.getJWTID();

        if (jti != null && ssoSessionValidator.isTokenReplayed(jti)) {
            throw new SSOAuthenticationException(SSOErrorCode.CONFIGURATION_ERROR);
        }

        return new TokenData(accessToken, refreshToken, idToken, expiresIn, idClaims, jti);
    }

    private SSOProfileResponse fetchAndEnrichProfile(String accessToken, JWTClaimsSet idClaims, String requestId) {
        SSOProfileResponse profileResponse = ssoProfileFetchService.fetchProfile(accessToken, requestId);

        String oktaProfileId = idClaims.getSubject();
        String profileId = idGeneratorService.generateUserId(oktaProfileId);
        String grpId = idGeneratorService.generateGroupId(oktaProfileId);

        log.info("Generated IDs - Okta profileId: {}, Exchange profileId: {}, grpId: {}",
            oktaProfileId, profileId, grpId);

        profileResponse.setProfileId(profileId);
        profileResponse.setGrpId(grpId);

        return profileResponse;
    }

    private void updateSessionWithDeviceAndTokens(SSOUserSession session, TokenData tokenData,
                                                  String authCode, String ipAddress,
                                                  String userAgent, HttpServletRequest request) {
        String deviceFingerprint = deviceFingerprintService.generateDeviceFingerprint(request);
        String browserFingerprint = deviceFingerprintService.generateBrowserFingerprint(request);

        sessionManagementService.trackSSODevice(
            session.getProfileId(),
            deviceFingerprint,
            browserFingerprint,
            request
        );

        sessionDataMapper.updateSessionWithTokens(
            session,
            authCode,
            tokenData.accessToken,
            tokenData.refreshToken,
            tokenData.idToken,
            tokenData.expiresIn,
            tokenData.jti,
            oktaConfig.getScope()
        );

        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setDeviceFingerprint(deviceFingerprint);
        session.setBrowserFingerprint(browserFingerprint);
        session.setIsActive(true);

        sessionRepo.save(session);
    }

    private Map<String, String> generateCustomTokens(SSOUserGroup userGroup, SSOProfileResponse profile) {
        Map<String, Object> registrationsMap = userGroupDataMapper.buildRegistrationsMap(userGroup);

        String customAccessToken = customTokenService.generateAccessToken(
            profile.getProfileId(),
            profile.getGrpId(),
            registrationsMap
        );

        String customRefreshToken = customTokenService.generateRefreshToken(
            profile.getProfileId(),
            profile.getGrpId()
        );

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", customAccessToken);
        tokens.put("refreshToken", customRefreshToken);

        return tokens;
    }

    private Map<String, String> generateCustomTokensWithExchangeIds(SSOUserGroup userGroup,
                                                                    String exchangeProfileId,
                                                                    String exchangeGrpId) {
        Map<String, Object> registrationsMap = userGroupDataMapper.buildRegistrationsMap(userGroup);

        String customAccessToken = customTokenService.generateAccessToken(
            exchangeProfileId,
            exchangeGrpId,
            registrationsMap
        );

        String customRefreshToken = customTokenService.generateRefreshToken(
            exchangeProfileId,
            exchangeGrpId
        );

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", customAccessToken);
        tokens.put("refreshToken", customRefreshToken);

        return tokens;
    }

    private void updateSessionWithCustomTokens(SSOUserSession session, Map<String, String> customTokens) {
        Map<String, Object> metadata = sessionDataMapper.buildCustomTokenMetadata(
            customTokens.get("accessToken"),
            customTokens.get("refreshToken")
        );

        sessionDataMapper.updateSessionMetadata(session, metadata);
        SSOUserSession savedSession = sessionRepo.saveAndFlush(Objects.requireNonNull(session));
        log.debug("Session saved with ID: {} for profileId: {}", savedSession.getId(), savedSession.getProfileId());
    }

    private void verifySessionPersistence(String profileId, String requestId) {
        Optional<SSOUserSession> verification = sessionRepo.findByProfileId(profileId);
        if (verification.isPresent()) {
            log.info("[RequestID: {}] Session verification SUCCESS - Found session ID: {} for profileId: {}",
                requestId, verification.get().getId(), profileId);
        } else {
            log.error("[RequestID: {}] Session verification FAILED - No session found for profileId: {}",
                requestId, profileId);
            throw new SSOSessionException(SSOErrorCode.CONFIGURATION_ERROR);
        }
    }

    private LoginResponse buildLoginResponse(Map<String, String> customTokens, String idToken,
                                            SSOProfileResponse profile) {
        return LoginResponse.builder()
                .accessToken(customTokens.get("accessToken"))
                .refreshToken(customTokens.get("refreshToken"))
                .idToken(idToken)
                .tokenType(SSOConstants.TOKEN_TYPE_BEARER)
                .expiresInSeconds((long) SSOConstants.DEFAULT_TOKEN_EXPIRY_SECONDS)
                .grpId(profile.getGrpId())
                .profileId(profile.getProfileId())
                .build();
    }

    private JWTClaimsSet validateRefreshToken(String customRefreshToken, String requestId) {
        try {
            return customTokenService.validateAndParseToken(customRefreshToken);
        } catch (Exception e) {
            log.error("[RequestID: {}] Refresh token validation failed", requestId, e);
            throw new SSOAuthenticationException(SSOErrorCode.SESSION_NOT_FOUND);
        }
    }

    private String extractGrpId(JWTClaimsSet claims) {
        try {
            Object gc = claims.getClaim("grp_id");
            return gc != null ? gc.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private SSOUserSession retrieveAndValidateSession(String profileId, String requestId) {
        log.debug("[RequestID: {}] Looking up session for profileId: {}", requestId, profileId);

        SSOUserSession session = sessionRepo.findByProfileId(profileId)
                .orElseThrow(() -> {
                    log.error("[RequestID: {}] No session found for profileId: {}", requestId, profileId);
                    return new SSOAuthenticationException(SSOErrorCode.SESSION_EXPIRED);
                });

        log.debug("[RequestID: {}] Session found for profileId: {}, status: {}, active: {}",
            requestId, profileId, session.getTokenStatus(), session.getIsActive());

        ssoSessionValidator.validateSession(session);
        return session;
    }

    private void refreshSSOTokens(SSOUserSession session, String requestId) {
        log.info("[RequestID: {}] Okta token expired, refreshing with SSO provider", requestId);

        Map<String, Object> tokenResponse = tokenExchangeStrategy.refreshTokens(
            session.getSsoRefreshToken(),
            requestId
        );

        session.setSsoAccessToken((String) tokenResponse.get("access_token"));

        if (tokenResponse.containsKey("refresh_token")) {
            session.setSsoRefreshToken((String) tokenResponse.get("refresh_token"));
        }

        session.setSsoIdToken((String) tokenResponse.get("id_token"));

        Integer expiresIn = sessionDataMapper.extractExpiresIn(tokenResponse);
        session.setSsoTokenExpiresAt(Instant.now().plusSeconds(
            expiresIn != null ? expiresIn : SSOConstants.DEFAULT_TOKEN_EXPIRY_SECONDS
        ));

        try {
            jwksService.validateAndParseToken(session.getSsoIdToken());
            log.info("[RequestID: {}] Refreshed ID token validated using JWKS", requestId);
        } catch (Exception e) {
            log.error("[RequestID: {}] Refreshed ID token validation failed", requestId, e);
            throw new SSOAuthenticationException(SSOErrorCode.INVALID_CREDENTIALS);
        }

        sessionRepo.save(session);
    }

    private LoginResponse buildRefreshResponse(Map<String, String> customTokens, String idToken,
                                               String profileId, String grpId) {
        return LoginResponse.builder()
                .accessToken(customTokens.get("accessToken"))
                .refreshToken(customTokens.get("refreshToken"))
                .idToken(idToken)
                .expiresInSeconds((long) SSOConstants.DEFAULT_TOKEN_EXPIRY_SECONDS)
                .grpId(grpId)
                .profileId(profileId)
                .build();
    }

    private static class TokenData {
        final String accessToken;
        final String refreshToken;
        final String idToken;
        final Integer expiresIn;
        final JWTClaimsSet idClaims;
        final String jti;

        TokenData(String accessToken, String refreshToken, String idToken,
                 Integer expiresIn, JWTClaimsSet idClaims, String jti) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.idToken = idToken;
            this.expiresIn = expiresIn;
            this.idClaims = idClaims;
            this.jti = jti;
        }
    }
}
