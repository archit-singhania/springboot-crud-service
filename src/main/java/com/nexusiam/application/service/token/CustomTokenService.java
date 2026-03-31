package com.nexusiam.application.service.token;

import com.nexusiam.application.service.user.UserRoleMappingService;
import com.nexusiam.infrastructure.config.oauth.ExchangeTokenConfig;
import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.core.domain.entity.SSOUserGroup;
import com.nexusiam.core.domain.entity.UserType;
import com.nexusiam.core.domain.repository.SSOUserGroupRepository;
import com.nexusiam.core.domain.repository.UserTypeRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.util.Base64URL;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomTokenService {

    private final SSOUserGroupRepository userGroupRepository;
    private final UserTypeRepository userTypeRepository;
    private final ExchangeTokenConfig tokenConfig;
    private final UserRoleMappingService userRoleMappingService;

    private RSAKey rsaKey;
    private JWSSigner signer;
    private JWSVerifier verifier;

    @Value("${jwt.privatekey}")
    private String privatekey;

    @Value("${jwt.publickey}")
    private String publickey;

    @Value("${jwt.rsaKey}")
    private String modulus;

    @Value("${jwt.keyId}")
    private String key;

    @PostConstruct
    public void init() {
        try {
            RSAKey rsaJwk = new RSAKey.Builder(new Base64URL(modulus), new Base64URL(publickey))
                    .privateExponent(new Base64URL(privatekey))
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(key)
                    .build();
            this.rsaKey = rsaJwk;
            signer = new RSASSASigner(rsaJwk.toPrivateKey());

            RSAKey publicJWK = new RSAKey.Builder(new Base64URL(modulus), new Base64URL(publickey))
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(key)
                    .build();

            verifier = new RSASSAVerifier(publicJWK.toRSAPublicKey());

            log.info("Custom token service initialized with key ID: {}", key);
        } catch (JOSEException e) {
            log.error("Failed to initialize custom token service", e);
            throw new IllegalStateException("Failed to initialize token service", e);
        }
    }

    public String generateAccessToken(String profileId, String grpId,
                                  Map<String, Object> registrations) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(SSOConstants.ACCESS_TOKEN_EXPIRY_SECONDS);

            SSOUserGroup userGroup = userGroupRepository.findByProfileId(profileId)
                    .orElse(null);

            UserType userTypeObj = userGroup != null ? userTypeRepository.findById(userGroup.getUserTypeId()).orElse(null) : null;
            String userType = userTypeObj != null ? userTypeObj.getType() : null;

            Map<String, Object> filteredRegistrations = registrations;
            if (userType != null && ("buyer".equalsIgnoreCase(userType) || "seller".equalsIgnoreCase(userType))) {
                filteredRegistrations = filterRegistrationsByUserType(registrations, userType);
            }

            String email = userGroup != null ? userGroup.getEmail() : null;
            List<String> extractedRoles = getAvailableRolesForUser(email, filteredRegistrations);
            log.debug("Available roles for profileId {}: {}", profileId, extractedRoles);

            String currentRole = extractedRoles.contains("SELLER") ? "SELLER" :
                               (!extractedRoles.isEmpty() ? extractedRoles.get(0) : "BUYER");

            List<String> portalIds = extractPortalIds(filteredRegistrations);

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(tokenConfig.getIssuer())
                    .subject(profileId)
                    .audience(tokenConfig.getAudience())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("profile_id", profileId)
                    .claim("grp_id", grpId)
                    .claim("current_role", currentRole)
                    .claim("available_roles", extractedRoles)
                    .claim("portal_ids", portalIds)
                    .claim("token_type", "ACCESS");

            if (filteredRegistrations != null && !filteredRegistrations.isEmpty()) {
                claimsBuilder.claim("registrations", filteredRegistrations);
            } else {
                claimsBuilder.claim("registrations", Map.of("portals", List.of()));
            }

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claimsBuilder.build()
            );

            signedJWT.sign(signer);

            log.info("Generated access token for profileId: {} with current_role: {}, available_roles: {}",
                profileId, currentRole, extractedRoles);

            return signedJWT.serialize();

        } catch (Exception e) {
            log.error("Failed to generate access token for profileId: {}, grpId: {}", profileId, grpId, e);
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    public String generateAccessTokenWithRole(String profileId, String grpId,
                                              String currentRole, List<String> availableRoles,
                                              Map<String, Object> registrations) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(SSOConstants.ACCESS_TOKEN_EXPIRY_SECONDS);

            SSOUserGroup userGroup = userGroupRepository.findByProfileId(profileId)
                    .orElse(null);

            UserType userTypeObj = userGroup != null ? userTypeRepository.findById(userGroup.getUserTypeId()).orElse(null) : null;
            String userType = userTypeObj != null ? userTypeObj.getType() : null;

            Map<String, Object> filteredRegistrations = registrations;
            if (userType != null && ("buyer".equalsIgnoreCase(userType) || "seller".equalsIgnoreCase(userType))) {
                filteredRegistrations = filterRegistrationsByUserType(registrations, userType);
            }

            List<String> portalIds = extractPortalIds(filteredRegistrations);

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(tokenConfig.getIssuer())
                    .subject(profileId)
                    .audience(tokenConfig.getAudience())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("profile_id", profileId)
                    .claim("grp_id", grpId)
                    .claim("current_role", currentRole)
                    .claim("available_roles", availableRoles)
                    .claim("portal_ids", portalIds)
                    .claim("token_type", "ACCESS");

            if (filteredRegistrations != null && !filteredRegistrations.isEmpty()) {
                claimsBuilder.claim("registrations", filteredRegistrations);
            } else {
                claimsBuilder.claim("registrations", Map.of("portals", List.of()));
            }

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claimsBuilder.build()
            );

            signedJWT.sign(signer);

            log.info("Generated access token with specific role for profileId: {}, current_role: {}, available_roles: {}",
                profileId, currentRole, availableRoles);

            return signedJWT.serialize();

        } catch (Exception e) {
            log.error("Failed to generate access token with role for profileId: {}, grpId: {}", profileId, grpId, e);
            throw new RuntimeException("Failed to generate access token with role", e);
        }
    }

    public String generateRefreshToken(String profileId, String grpId) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(SSOConstants.REFRESH_TOKEN_EXPIRY_SECONDS);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(tokenConfig.getIssuer())
                    .subject(profileId)
                    .audience(tokenConfig.getAudience())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("grp_id", grpId)
                    .claim("token_type", "REFRESH")
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claims);

            signedJWT.sign(signer);

            String token = signedJWT.serialize();
            log.info("Generated custom refresh token for profileId: {}, grpId: {}", profileId, grpId);

            return token;
        } catch (Exception e) {
            log.error("Failed to generate refresh token", e);
            throw new RuntimeException("Failed to generate refresh token", e);
        }
    }

    public JWTClaimsSet validateAndParseToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new RuntimeException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (!tokenConfig.getIssuer().equals(claims.getIssuer())) {
                throw new RuntimeException("Invalid token issuer");
            }

            if (!claims.getAudience().contains(tokenConfig.getAudience())) {
                throw new RuntimeException("Invalid token audience");
            }

            Date expirationTime = claims.getExpirationTime();
            Date currentTime = new Date();
            long timeUntilExpiryMs = expirationTime.getTime() - currentTime.getTime();
            long timeUntilExpirySec = timeUntilExpiryMs / 1000;

            log.info("🔍 [Token Validation] Token expiry check:");
            log.info("  → Token expires at: {}", expirationTime);
            log.info("  → Current server time: {}", currentTime);
            log.info("  → Time until expiry: {} seconds ({} ms)", timeUntilExpirySec, timeUntilExpiryMs);
            log.info("  → Subject (profileId): {}", claims.getSubject());

            if (claims.getExpirationTime().before(new Date())) {
                log.error("❌ [Token Validation] Token EXPIRED by {} seconds", Math.abs(timeUntilExpirySec));
                throw new RuntimeException("Token expired");
            }

            log.info("✅ [Token Validation] Token is valid with {} seconds remaining", timeUntilExpirySec);
            log.debug("Token validated successfully for subject: {}", claims.getSubject());
            return claims;

        } catch (Exception e) {
            log.error("Token validation failed", e);
            throw new RuntimeException("Invalid or expired token", e);
        }
    }

    public JWTClaimsSet validateIdToken(String idToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(idToken);

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                throw new RuntimeException("ID token expired");
            }

            return claims;
        } catch (Exception e) {
            log.error("ID token validation failed", e);
            throw new RuntimeException("Invalid or expired ID token", e);
        }
    }

    public String getJWKS() {
        try {
            return rsaKey.toPublicJWK().toJSONString();
        } catch (Exception e) {
            log.error("Failed to export JWKS", e);
            throw new RuntimeException("Failed to export JWKS", e);
        }
    }

    public boolean isCustomToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            String tokenType = String.valueOf(claims.getClaim("token_type")).toUpperCase();

            return tokenConfig.getIssuer().equals(claims.getIssuer())
                    && claims.getAudience() != null
                    && claims.getAudience().contains(tokenConfig.getAudience())
                    && ("ACCESS".equals(tokenType) || "REFRESH".equals(tokenType));
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> filterRegistrationsByUserType(Map<String, Object> registrations, String hardcodedUserType) {
        if (hardcodedUserType == null ||
            (!"buyer".equalsIgnoreCase(hardcodedUserType) && !"seller".equalsIgnoreCase(hardcodedUserType))) {
            return registrations;
        }

        if (registrations == null || !registrations.containsKey("portals")) {
            return registrations;
        }

        try {
            List<UserType> validUserTypes = userTypeRepository.findAll().stream()
                    .filter(ut -> hardcodedUserType.equalsIgnoreCase(ut.getType()))
                    .toList();

            if (validUserTypes.isEmpty()) {
                log.warn("No user types found for hardcoded type: {}", hardcodedUserType);
                return registrations;
            }

            List<String> validRoles = validUserTypes.stream()
                    .map(UserType::getRole)
                    .toList();

            log.debug("Valid roles for user type '{}': {}", hardcodedUserType, validRoles);

            Object portalsObj = registrations.get("portals");
            if (!(portalsObj instanceof List)) {
                return registrations;
            }

            List<?> portalsList = (List<?>) portalsObj;

            List<Map<?, ?>> filteredPortals = new java.util.ArrayList<>();
            for (Object portalObj : portalsList) {
                if (!(portalObj instanceof Map)) {
                    continue;
                }

                Map<?, ?> portal = (Map<?, ?>) portalObj;
                Object roleObj = portal.get("role");

                if (roleObj instanceof String) {
                    String portalRole = (String) roleObj;
                    if (validRoles.stream().anyMatch(validRole -> validRole.equalsIgnoreCase(portalRole))) {
                        filteredPortals.add(portal);
                        log.debug("Including portal with role: {}", portalRole);
                    } else {
                        log.debug("Excluding portal with role: {} (not in valid roles)", portalRole);
                    }
                }
            }

            Map<String, Object> filteredRegistrations = new java.util.HashMap<>(registrations);
            filteredRegistrations.put("portals", filteredPortals);

            log.info("Filtered portals for user type '{}': {} out of {} portals",
                    hardcodedUserType, filteredPortals.size(), portalsList.size());

            return filteredRegistrations;

        } catch (Exception e) {
            log.error("Error filtering registrations by user type", e);
            return registrations;
        }
    }

    private List<String> extractRolesFromRegistrations(Map<String, Object> registrations, String hardcodedUserType) {
        Set<String> roles = new java.util.LinkedHashSet<>();

        if (registrations == null || !registrations.containsKey("portals")) {
            return new ArrayList<>(roles);
        }

        try {
            Object portalsObj = registrations.get("portals");
            if (!(portalsObj instanceof List)) {
                log.warn("Portals is not a List type");
                return new ArrayList<>(roles);
            }

            List<?> portalsList = (List<?>) portalsObj;

            for (Object portalObj : portalsList) {
                if (!(portalObj instanceof Map)) {
                    continue;
                }

                Map<?, ?> portal = (Map<?, ?>) portalObj;
                Object roleObj = portal.get("role");
                Object statusObj = portal.get("status");

                if (!(roleObj instanceof String) || !(statusObj instanceof String)) {
                    continue;
                }

                String role = (String) roleObj;
                String status = (String) statusObj;

                if (("active".equalsIgnoreCase(status) || "Active".equalsIgnoreCase(status)) && role != null) {
                    String mappedRole = mapPortalRoleToSystemRole(role);
                    roles.add(mappedRole);
                }
            }

        } catch (ClassCastException e) {
            log.error("Type casting error while extracting roles from registrations", e);
        } catch (Exception e) {
            log.error("Error extracting roles from registrations", e);
        }

        return new ArrayList<>(roles);
    }

    private String mapPortalRoleToSystemRole(String portalRole) {
        if (portalRole == null) return "BUYER";

        String normalized = portalRole.toLowerCase().trim();

        if (normalized.contains("recycl") || normalized.contains("dismantle") ||
            normalized.contains("process") || normalized.contains("treatment") ||
            normalized.contains("facility")) {
            return "SELLER";
        }

        else if (normalized.contains("producer") || normalized.contains("manufacturer") ||
                   normalized.contains("generator") || normalized.contains("supplier") ||
                   normalized.contains("brand") || normalized.contains("owner") ||
                   normalized.contains("pibo")) {
            return "BUYER";
        }

        return "BUYER";
    }

    private List<String> extractPortalIds(Map<String, Object> registrations) {
        List<String> portalIds = new ArrayList<>();

        if (registrations == null || !registrations.containsKey("portals")) {
            return portalIds;
        }

        try {
            Object portalsObj = registrations.get("portals");
            if (!(portalsObj instanceof List)) {
                log.warn("Portals is not a List type in extractPortalIds");
                return portalIds;
            }

            List<?> portalsList = (List<?>) portalsObj;
            Set<String> uniquePortalIds = new LinkedHashSet<>();

            for (Object portalObj : portalsList) {
                if (!(portalObj instanceof Map)) {
                    continue;
                }

                Map<?, ?> portal = (Map<?, ?>) portalObj;
                Object portalIdObj = portal.get("portal_id");

                if (portalIdObj instanceof String) {
                    String portalId = (String) portalIdObj;
                    uniquePortalIds.add(portalId);
                    log.debug("Extracted portal_id: {}", portalId);
                }
            }

            portalIds.addAll(uniquePortalIds);
            log.debug("Total unique portal_ids extracted: {}", portalIds.size());

        } catch (Exception e) {
            log.error("Error extracting portal_ids from registrations", e);
        }

        return portalIds;
    }

    private List<String> getAvailableRolesForUser(String email, Map<String, Object> registrations) {
        if (email == null) {

            return extractRolesFromRegistrations(registrations, null);
        }

        try {

            List<Map<String, Object>> registrationsList = convertRegistrationsToList(registrations);

            List<String> roles = userRoleMappingService.getAvailableRoleStrings(email, registrationsList);

            log.debug("Got available roles from UserRoleMappingService for email {}: {}", email, roles);
            return roles;

        } catch (Exception e) {
            log.error("Error getting roles from UserRoleMappingService, falling back to extraction", e);

            return extractRolesFromRegistrations(registrations, null);
        }
    }

    private List<Map<String, Object>> convertRegistrationsToList(Map<String, Object> registrations) {
        if (registrations == null || !registrations.containsKey("portals")) {
            return new ArrayList<>();
        }

        Object portalsObj = registrations.get("portals");
        if (!(portalsObj instanceof List)) {
            return new ArrayList<>();
        }

        List<?> portalsList = (List<?>) portalsObj;
        List<Map<String, Object>> result = new ArrayList<>();

        for (Object portalObj : portalsList) {
            if (portalObj instanceof Map<?, ?>) {
                Map<?, ?> portalMap = (Map<?, ?>) portalObj;
                Map<String, Object> portal = new java.util.HashMap<>();
                for (Map.Entry<?, ?> entry : portalMap.entrySet()) {
                    if (entry.getKey() instanceof String) {
                        portal.put((String) entry.getKey(), entry.getValue());
                    }
                }
                result.add(portal);
            }
        }

        return result;
    }
}
