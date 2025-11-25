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
        Integer expiresIn = null;
        try {
            Object ex = oktaTokenResponse.get("expires_in");
            if (ex instanceof Integer) {
                expiresIn = (Integer) ex;
            } else if (ex instanceof Number) {
                expiresIn = ((Number) ex).intValue();
            }
        } catch (Exception ignored) { /* fall through, use default later */ }
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
        String orgId = null;
        try {
            Object orgClaim = idClaims.getClaim("org_id");
            if (orgClaim != null) orgId = orgClaim.toString();
        } catch (Exception ignored) { /* optional claim */ }
        String jti = idClaims.getJWTID();

        log.info("Token validated successfully for profileId: {}, orgId: {}", profileId, orgId);


        // ✅ OPTIONAL: Also validate access token if it's a JWT (some providers return opaque tokens)
        try {
            JWTClaimsSet accessTokenClaims = jwksService.validateAndParseToken(accessToken);
            log.info("Access token validated successfully using JWKS");
        } catch (Exception e) {
            log.debug("Access token validation skipped or failed (opaque token likely): {}", e.getMessage());
            // Continue - not fatal
        }

        // Fetch user profile (userinfo endpoint) — fallback to profileId-based email if necessary
        String email;
        try {
            log.info("Fetching user profile from SSO");
            Map<String, Object> userInfo = oktaWebClient.get()
                    .uri(profileEndpoint)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (userInfo != null) {
                email = (String) userInfo.getOrDefault("email", userInfo.get("preferred_username"));
                if (email == null) {
                    email = profileId + "@okta.local";
                }
            } else {
                email = profileId + "@okta.local";
            }
            log.info("User email resolved as: {}", email);
        } catch (Exception e) {
            log.warn("Failed to fetch userinfo, using profileId as email", e);
            email = profileId + "@okta.local";
        }

        // Prepare or update SSOExchangeMaster record
        SSOExchangeMaster ssoRecord = ssoRepo.findByProfileId(profileId)
                .orElse(SSOExchangeMaster.builder()
                        .profileId(profileId)
                        .orgId(orgId != null ? orgId : profileId)
                        .email(email)
                        .createdAt(Instant.now())
                        .build());

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
        ssoRecord.setEmail(email);
        ssoRecord.setStatus("Active");
        ssoRecord.setExchangeAccess("allowed");
        ssoRecord.setUpdatedAt(Instant.now());

        if (ssoRecord.getRegistrations() == null) {
            String mockRegistrations = "[{\"portal_id\":\"demo\",\"portal_name\":\"Demo Portal\",\"role\":\"User\"}]";
            ssoRecord.setRegistrations(mockRegistrations);
        }

        ssoRepo.save(ssoRecord);

        // Remove PKCE value from Redis
        redisTemplate.delete("pkce:" + state);

        // Generate custom tokens
        String customAccessToken;
        String customRefreshToken;
        try {
            List<Map<String, Object>> registrationsList = objectMapper.readValue(
                    ssoRecord.getRegistrations(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

            Map<String, Object> registrationsMap = new HashMap<>();
            registrationsMap.put("portals", registrationsList);
            registrationsMap.put("status", ssoRecord.getStatus());
            registrationsMap.put("exchange_access", ssoRecord.getExchangeAccess());

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

    private SSOProfileResponse fetchProfileFromSSO(String oktaAccessToken) {
        log.info("Fetching user profile from SSO");

        SSOProfileResponse profileResponse;
        try {
            profileResponse = oktaWebClient.get()
                    .uri(profileEndpoint)
                    .header("Authorization", "Bearer " + oktaAccessToken)
                    .retrieve()
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

        log.info("Profile retrieved from SSO for profileId: {}", profileResponse.getProfileId());
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
    public SSOExchangeMaster getOrganizationProfile(String orgId, String serviceToken) {
        log.info("Fetching organization profile for orgId: {}", orgId);

        String orgEndpoint = "/api/v1/profile/org/" + orgId;
        SSOProfileResponse profileResponse;
        try {
            profileResponse = oktaWebClient.get()
                    .uri(orgEndpoint)
                    .header("Authorization", "Bearer " + serviceToken)
                    .retrieve()
                    .bodyToMono(SSOProfileResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch organization profile", e);
            throw new RuntimeException("Failed to fetch organization profile", e);
        }

        if (profileResponse == null) {
            log.error("Empty organization profile response");
            throw new RuntimeException("Organization not found");
        }

        SSOExchangeMaster ssoRecord = ssoRepo.findByOrgId(orgId)
                .orElse(SSOExchangeMaster.builder()
                        .orgId(orgId)
                        .createdAt(Instant.now())
                        .build());

        updateProfileData(ssoRecord, profileResponse);
        ssoRecord.setServiceToken(serviceToken);
        ssoRecord.setServiceTokenIssuedAt(Instant.now());
        ssoRecord.setServiceTokenExpiresAt(Instant.now().plusSeconds(900));
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

    private void updateProfileData(SSOExchangeMaster record, SSOProfileResponse profile) {
        if (profile == null) return;

        record.setOrgId(profile.getOrgId());
        record.setProfileId(profile.getProfileId());
        record.setCompanyName(profile.getCompanyName());
        record.setCompanyAddress(profile.getCompanyAddress());
        record.setState(profile.getState());
        record.setPincode(profile.getPincode());
        record.setGstNumber(profile.getGstNumber());
        record.setCinNumber(profile.getCinNumber());
        record.setPanNumber(profile.getPanNumber());
        record.setAuthorizedPersonName(profile.getAuthorizedPersonName());
        record.setDesignation(profile.getDesignation());
        record.setEmail(profile.getEmail());
        record.setMobile(profile.getMobile());
        record.setLandline(profile.getLandline());
        record.setStatus(profile.getStatus());
        record.setComplianceStatus(profile.getComplianceStatus());
        record.setExchangeAccess(profile.getExchangeAccess());
        record.setValidTill(profile.getValidTill());
        record.setUpdatedAt(Instant.now());

        try {
            if (profile.getRegistrations() != null) {
                record.setRegistrations(objectMapper.writeValueAsString(profile.getRegistrations()));
            }
            record.setRawSsoPayload(objectMapper.writeValueAsString(profile));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize profile data to JSON", e);
            throw new RuntimeException("Failed to process profile data", e);
        }
    }
}
