package com.example.demo.service;

import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.SSOProfileResponse;
import com.example.demo.model.SSOExchangeMaster;
import com.example.demo.repository.SSOExchangeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class SSOExchangeServiceImpl implements SSOExchangeService {

    private final SSOExchangeRepository ssoRepo;
    private final CustomTokenService customTokenService;
    private final JwksService jwksService;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebClient oktaWebClient;
    private final ObjectMapper objectMapper;

    @Value("${okta.oauth2.client-id}")
    private String clientId;

    @Value("${okta.oauth2.client-secret}")
    private String clientSecret;

    @Value("${okta.oauth2.authorization-endpoint}")
    private String authorizationEndpoint;

    @Value("${okta.oauth2.token-endpoint}")
    private String tokenEndpoint;

    @Value("${okta.oauth2.profile-endpoint}")
    private String profileEndpoint;

    @Value("${okta.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${okta.oauth2.scope}")
    private String scope;

    @Override
    public Map<String, String> requestAuthCode(String state) {
        log.info("Generating PKCE and authorization URL for state: {}", state);

        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        redisTemplate.opsForValue().set(
                "pkce:" + state,
                codeVerifier,
                300,
                TimeUnit.SECONDS
        );

        log.debug("PKCE verifier stored in Redis for state: {}", state);

        String authUrl = UriComponentsBuilder.fromUriString(authorizationEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .toUriString();

        log.info("Authorization URL generated successfully for state: {}", state);

        return Map.of(
                "authorizationUrl", authUrl,
                "state", state,
                "message", "Redirect user to authorizationUrl to login via Okta"
        );
    }

    @Override
    @Transactional
    public LoginResponse exchangeAuthCode(String authCode, String state) {
        log.info("Exchanging authorization code for tokens with state: {}", state);

        String codeVerifier = redisTemplate.opsForValue().get("pkce:" + state);
        if (codeVerifier == null) {
            log.error("PKCE verifier not found or expired for state: {}", state);
            throw new RuntimeException("Invalid or expired state parameter. Please retry login.");
        }

        log.debug("PKCE verifier retrieved from Redis for state: {}", state);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", authCode);
        formData.add("redirect_uri", redirectUri);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("code_verifier", codeVerifier);

        Map<String, Object> oktaTokenResponse;
        try {
            oktaTokenResponse = oktaWebClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("Okta error response: {}", body);
                                        return new RuntimeException("Okta error: " + body);
                                    })
                    )
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to exchange auth code with Okta", e);
            throw new RuntimeException("Failed to exchange authorization code. Please try again.", e);
        }

        if (oktaTokenResponse == null || !oktaTokenResponse.containsKey("access_token")) {
            log.error("Invalid token response from Okta");
            throw new RuntimeException("Invalid response from SSO server");
        }

        String accessToken = (String) oktaTokenResponse.get("access_token");
        String refreshToken = (String) oktaTokenResponse.get("refresh_token");
        String idToken = (String) oktaTokenResponse.get("id_token");
        Integer expiresIn = extractExpiresIn(oktaTokenResponse);
        String tokenType = "Bearer";

        log.info("Okta tokens received, expires in {} seconds", expiresIn);

        // ✅ VALIDATE ID TOKEN USING JWKS
        JWTClaimsSet idClaims;
        try {
            idClaims = jwksService.validateAndParseToken(idToken);
            log.info("ID token validated successfully using JWKS");
        } catch (Exception e) {
            log.error("ID token JWKS validation failed", e);
            throw new RuntimeException("Invalid ID token from SSO server", e);
        }

        String profileId = idClaims.getSubject();
        String jti = idClaims.getJWTID();

        log.info("Token validated successfully for profileId: {}", profileId);

        // ✅ OPTIONAL: Validate access token if it's a JWT
        try {
            JWTClaimsSet accessTokenClaims = jwksService.validateAndParseToken(accessToken);
            log.info("Access token validated successfully using JWKS");
        } catch (Exception e) {
            log.debug("Access token validation skipped or failed (opaque token likely): {}", e.getMessage());
        }

        // ✅ FETCH FULL PROFILE FROM COMMON SSO (or Okta for testing)
        log.info("Fetching complete profile from SSO");
        SSOProfileResponse profileResponse = fetchProfileFromSSO(accessToken);

        String email = profileResponse.getEmail();
        if (email == null || email.isEmpty()) {
            email = profileId + "@exchange.local";
        }

        String actualOrgId = profileResponse.getOrgId();
        if (actualOrgId == null || actualOrgId.isEmpty()) {
            actualOrgId = profileId; // fallback
        }

        log.info("Profile retrieved: email={}, orgId={}, companyName={}",
                email, actualOrgId, profileResponse.getCompanyName());

        // Find or create SSO record
        SSOExchangeMaster ssoRecord = ssoRepo.findByProfileId(profileId)
                .orElse(SSOExchangeMaster.builder()
                        .profileId(profileId)
                        .orgId(actualOrgId)
                        .email(email)
                        .createdAt(Instant.now())
                        .build());

        // Update with ALL Common SSO profile data
        updateProfileData(ssoRecord, profileResponse);

        // Update tokens
        ssoRecord.setAuthCode(authCode);
        ssoRecord.setAuthCodeExpiresAt(Instant.now().plusSeconds(60));
        ssoRecord.setAccessToken(accessToken);
        ssoRecord.setRefreshToken(refreshToken);
        ssoRecord.setIdToken(idToken);
        ssoRecord.setTokenType(tokenType);
        ssoRecord.setScope(scope);
        ssoRecord.setIssuedAt(Instant.now());
        ssoRecord.setExpiresAt(Instant.now().plusSeconds(expiresIn != null ? expiresIn : 900));
        ssoRecord.setJti(jti);
        ssoRecord.setTokenStatus("ACTIVE");
        ssoRecord.setLastLoginOn(Instant.now());
        ssoRecord.setUpdatedAt(Instant.now());

        ssoRepo.save(ssoRecord);

        // Remove PKCE value from Redis
        redisTemplate.delete("pkce:" + state);

        // Generate custom tokens with registrations data
        String customAccessToken;
        String customRefreshToken;
        try {
            Map<String, Object> registrationsMap = buildRegistrationsMap(ssoRecord, profileResponse);

            customAccessToken = customTokenService.generateAccessToken(
                    profileId,
                    ssoRecord.getOrgId(),
                    registrationsMap
            );
            customRefreshToken = customTokenService.generateRefreshToken(profileId, ssoRecord.getOrgId());

            ssoRecord.setCustomAccessToken(customAccessToken);
            ssoRecord.setCustomRefreshToken(customRefreshToken);
            ssoRepo.save(ssoRecord);

            log.info("Generated custom Exchange tokens for profileId: {}", profileId);
        } catch (Exception e) {
            log.error("Failed to generate custom tokens", e);
            throw new RuntimeException("Failed to generate custom tokens", e);
        }

        log.info("Token exchange completed successfully for profileId: {}", profileId);

        return LoginResponse.builder()
                .accessToken(customAccessToken)
                .refreshToken(customRefreshToken)
                .idToken(idToken)
                .tokenType(tokenType)
                .expiresInSeconds(900L)
                .orgId(ssoRecord.getOrgId())
                .profileId(profileId)
                .build();
    }

    // Helper method to extract expires_in
    private Integer extractExpiresIn(Map<String, Object> tokenResponse) {
        try {
            Object ex = tokenResponse.get("expires_in");
            if (ex instanceof Integer) {
                return (Integer) ex;
            } else if (ex instanceof Number) {
                return ((Number) ex).intValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // Helper method to build registrations map
    private Map<String, Object> buildRegistrationsMap(SSOExchangeMaster record, SSOProfileResponse profile) {
        Map<String, Object> registrationsMap = new HashMap<>();

        try {
            if (profile.getRegistrations() != null && !profile.getRegistrations().isEmpty()) {
                registrationsMap.put("portals", profile.getRegistrations());
            } else if (record.getRegistrations() != null) {
                // Parse existing registrations from DB
                List<Map<String, Object>> registrationsList = objectMapper.readValue(
                        record.getRegistrations(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );
                registrationsMap.put("portals", registrationsList);
            } else {
                // Only use mock data as last resort
                List<Map<String, Object>> mockData = List.of(
                        Map.of("portal_id", "demo", "portal_name", "Demo Portal", "role", "User")
                );
                registrationsMap.put("portals", mockData);
            }

            registrationsMap.put("status", profile.getStatus() != null ? profile.getStatus() : "Active");
            registrationsMap.put("exchange_access", profile.getExchangeAccess() != null ? profile.getExchangeAccess() : "allowed");

        } catch (Exception e) {
            log.warn("Failed to parse registrations, using minimal data", e);
            registrationsMap.put("portals", List.of());
            registrationsMap.put("status", "Active");
            registrationsMap.put("exchange_access", "allowed");
        }

        return registrationsMap;
    }

    private SSOProfileResponse fetchProfileFromSSO(String oktaAccessToken) {
        log.info("Fetching user profile from SSO");

        // 🔄 SWITCH THIS IN PRODUCTION:
        // Development/Testing: Use Okta's userinfo endpoint
        // Production: Use Common SSO profile endpoint

        // For testing with Okta (limited data):
        String profileApiEndpoint = profileEndpoint; // Okta endpoint from config

        // For production with Common SSO (full data):
        // String profileApiEndpoint = "https://sso.cpcb.gov.in/api/v1/profile/me";

        SSOProfileResponse profileResponse;
        try {
            profileResponse = oktaWebClient.get()
                    .uri(profileApiEndpoint)
                    .header("Authorization", "Bearer " + oktaAccessToken)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("SSO profile error: {}", body);
                                        return new RuntimeException("SSO profile error: " + body);
                                    })
                    )
                    .bodyToMono(SSOProfileResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch profile from SSO", e);
            throw new RuntimeException("Failed to fetch user profile from SSO server", e);
        }

        if (profileResponse == null) {
            log.error("Empty profile response from SSO");
            throw new RuntimeException("Failed to retrieve user profile");
        }

        log.info("Profile retrieved from SSO - profileId: {}, orgId: {}, company: {}",
                profileResponse.getProfileId(),
                profileResponse.getOrgId(),
                profileResponse.getCompanyName());

        return profileResponse;
    }

    @Override
    @Transactional
    public SSOExchangeMaster getProfile(String customAccessToken) {
        log.info("Getting profile for custom access token");

        // Validate custom token
        JWTClaimsSet claims;
        try {
            claims = customTokenService.validateAndParseToken(customAccessToken);
        } catch (Exception e) {
            log.error("Custom token validation failed", e);
            throw new RuntimeException("Invalid or expired access token", e);
        }

        String profileId = claims.getSubject();

        SSOExchangeMaster ssoRecord = ssoRepo.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException("SSO record not found"));

        // Optional: re-validate stored ID token using JWKS
        if (ssoRecord.getIdToken() != null) {
            try {
                jwksService.validateAndParseToken(ssoRecord.getIdToken());
                log.debug("Stored ID token still valid");
            } catch (Exception e) {
                log.warn("Stored ID token expired or invalid: {}", e.getMessage());
                // Optionally trigger refresh here
            }
        }

        return ssoRecord;
    }

    @Override
    @Transactional
    public LoginResponse refreshAccessToken(String customRefreshToken) {
        log.info("Refreshing custom Exchange token");

        JWTClaimsSet claims;
        try {
            claims = customTokenService.validateAndParseToken(customRefreshToken);
        } catch (Exception e) {
            log.error("Custom refresh token validation failed", e);
            throw new RuntimeException("Invalid or expired refresh token. Please login again.", e);
        }

        String profileId = claims.getSubject();
        String orgId = null;
        try {
            Object oc = claims.getClaim("org_id");
            if (oc != null) orgId = oc.toString();
        } catch (Exception ignored) { /* optional */ }

        log.info("Custom refresh token validated for profileId: {}", profileId);

        SSOExchangeMaster ssoRecord = ssoRepo.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Refresh Okta token if expired
        if (isOktaTokenExpired(ssoRecord)) {
            log.info("Okta token expired, refreshing with Okta");
            refreshOktaToken(ssoRecord);

            // ✅ VALIDATE NEW ID TOKEN USING JWKS
            try {
                jwksService.validateAndParseToken(ssoRecord.getIdToken());
                log.info("Refreshed ID token validated using JWKS");
            } catch (Exception e) {
                log.error("Refreshed ID token validation failed", e);
                throw new RuntimeException("Invalid refreshed ID token", e);
            }
        }

        // Optionally update profile from SSO
        SSOProfileResponse profileResponse = fetchProfileFromSSO(ssoRecord.getAccessToken());

        Map<String, Object> registrationsMap = new HashMap<>();
        try {
            if (profileResponse.getRegistrations() != null) {
                registrationsMap.put("portals", profileResponse.getRegistrations());
                registrationsMap.put("status", profileResponse.getStatus());
                registrationsMap.put("exchange_access", profileResponse.getExchangeAccess());
            }
        } catch (Exception e) {
            log.warn("Failed to parse registrations for token", e);
        }

        String newCustomAccessToken = customTokenService.generateAccessToken(
                profileId,
                orgId,
                registrationsMap
        );

        String newCustomRefreshToken = customTokenService.generateRefreshToken(
                profileId,
                orgId
        );

        ssoRecord.setCustomAccessToken(newCustomAccessToken);
        ssoRecord.setCustomRefreshToken(newCustomRefreshToken);
        ssoRecord.setUpdatedAt(Instant.now());

        updateProfileData(ssoRecord, profileResponse);
        ssoRepo.save(ssoRecord);

        log.info("Custom token refresh completed for profileId: {}", profileId);

        return LoginResponse.builder()
                .accessToken(newCustomAccessToken)
                .refreshToken(newCustomRefreshToken)
                .idToken(ssoRecord.getIdToken())
                .expiresInSeconds(900L)
                .orgId(orgId)
                .profileId(profileId)
                .build();
    }

    private void refreshOktaToken(SSOExchangeMaster ssoRecord) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", ssoRecord.getRefreshToken());
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        try {
            Map<String, Object> tokenResponse = oktaWebClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
                ssoRecord.setAccessToken((String) tokenResponse.get("access_token"));
                ssoRecord.setRefreshToken((String) tokenResponse.get("refresh_token"));
                ssoRecord.setIdToken((String) tokenResponse.get("id_token"));
                Integer expiresIn = null;
                try {
                    Object ex = tokenResponse.get("expires_in");
                    if (ex instanceof Integer) {
                        expiresIn = (Integer) ex;
                    } else if (ex instanceof Number) {
                        expiresIn = ((Number) ex).intValue();
                    }
                } catch (Exception ignored) { /* use default */ }
                ssoRecord.setExpiresAt(Instant.now().plusSeconds(expiresIn != null ? expiresIn : 900));
                log.info("Okta token refreshed successfully");
            } else {
                log.warn("Okta refresh did not return expected tokens");
            }
        } catch (Exception e) {
            log.error("Failed to refresh Okta token", e);
            throw new RuntimeException("Failed to refresh SSO token", e);
        }
    }

    private boolean isOktaTokenExpired(SSOExchangeMaster record) {
        if (record.getExpiresAt() == null) {
            return true;
        }
        return record.getExpiresAt().isBefore(Instant.now().plusSeconds(120));
    }

    @Override
    @Transactional
    public SSOExchangeMaster getOrganizationProfile(String orgId) {
        log.info("Fetching organization profile for orgId: {}", orgId);

        // Call Common SSO organization endpoint
        String orgEndpoint = profileEndpoint; // For testing with Okta or real SSO

        SSOProfileResponse profileResponse;
        try {
            profileResponse = oktaWebClient.get()
                    .uri(orgEndpoint)
                    //.header("Authorization", "Bearer " + accessToken) // optional if user token
                    .retrieve()
                    .bodyToMono(SSOProfileResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch organization profile", e);
            throw new RuntimeException("Failed to fetch organization profile", e);
        }

        if (profileResponse == null) {
            throw new RuntimeException("Organization not found: " + orgId);
        }

        // Find or create SSO record
        SSOExchangeMaster ssoRecord = ssoRepo.findByOrgId(orgId)
                .orElse(SSOExchangeMaster.builder()
                        .orgId(orgId)
                        .createdAt(Instant.now())
                        .build());

        // Update with profile data
        updateProfileData(ssoRecord, profileResponse);

        ssoRecord.setUpdatedAt(Instant.now());
        ssoRepo.save(ssoRecord);

        log.info("Organization profile updated for orgId: {}", orgId);
        return ssoRecord;
    }

    private String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate code challenge", e);
            throw new RuntimeException("Failed to generate PKCE code challenge", e);
        }
    }

    /**
     * Update SSO record with profile data from Common SSO
     */
    private void updateProfileData(SSOExchangeMaster record, SSOProfileResponse profile) {
        if (profile == null) return;

        // Update organization fields
        if (profile.getOrgId() != null) {
            record.setOrgId(profile.getOrgId());
        }

        if (profile.getProfileId() != null) {
            record.setProfileId(profile.getProfileId());
        }

        // Update company details
        record.setCompanyName(profile.getCompanyName());
        record.setCompanyAddress(profile.getCompanyAddress());
        record.setState(profile.getState());
        record.setPincode(profile.getPincode());
        record.setGstNumber(profile.getGstNumber());
        record.setCinNumber(profile.getCinNumber());
        record.setPanNumber(profile.getPanNumber());

        // Update authorized person
        record.setAuthorizedPersonName(profile.getAuthorizedPersonName());
        record.setDesignation(profile.getDesignation());
        record.setEmail(profile.getEmail());
        record.setMobile(profile.getMobile());
        record.setLandline(profile.getLandline());

        // Update status fields
        record.setStatus(profile.getStatus());
        record.setComplianceStatus(profile.getComplianceStatus());
        record.setExchangeAccess(profile.getExchangeAccess());
        record.setValidTill(profile.getValidTill());

        // Update registrations if available
        try {
            if (profile.getRegistrations() != null && !profile.getRegistrations().isEmpty()) {
                record.setRegistrations(objectMapper.writeValueAsString(profile.getRegistrations()));
            }

            // Store raw payload for audit
            record.setRawSsoPayload(objectMapper.writeValueAsString(profile));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize profile data", e);
        }

        record.setUpdatedAt(Instant.now());

        log.debug("Profile data updated for profileId: {}", record.getProfileId());
    }
}
