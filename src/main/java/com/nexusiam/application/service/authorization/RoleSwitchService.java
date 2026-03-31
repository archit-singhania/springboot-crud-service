package com.nexusiam.application.service.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusiam.application.service.mapper.UserGroupDataMapper;
import com.nexusiam.application.service.session.SessionManagementService;
import com.nexusiam.application.service.token.CustomTokenService;
import com.nexusiam.application.service.user.RegistrationService;
import com.nexusiam.application.service.user.UserRoleMappingService;
import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.shared.constants.SSOErrorCode;
import com.nexusiam.application.dto.response.RoleSwitchResponse;
import com.nexusiam.presentation.exception.SSOAuthenticationException;
import com.nexusiam.core.domain.entity.SSOUserGroup;
import com.nexusiam.core.domain.entity.SSOUserSession;
import com.nexusiam.core.domain.repository.SSOUserGroupRepository;
import com.nexusiam.core.domain.repository.SSOUserSessionRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoleSwitchService {

    private final SSOUserSessionRepository sessionRepository;
    private final SSOUserGroupRepository userGroupRepository;
    private final CustomTokenService customTokenService;
    private final SessionManagementService sessionManagementService;
    private final UserRoleMappingService userRoleMappingService;
    private final UserGroupDataMapper userGroupDataMapper;
    private final RegistrationService registrationService;

    @Transactional
    public RoleSwitchResponse switchRole(String currentAccessToken, String newRole) {
        String requestId = java.util.UUID.randomUUID().toString();
        log.info("[RequestID: {}] Starting role switch to: {}", requestId, newRole);

        try {

            JWTClaimsSet claims = customTokenService.validateAndParseToken(currentAccessToken);
            String profileId = claims.getSubject();
            String grpId = extractGrpId(claims);

            log.info("[RequestID: {}] Role switch requested for profileId: {}, grpId: {}",
                requestId, profileId, grpId);

            SSOUserSession session = sessionRepository.findByProfileId(profileId)
                    .orElseThrow(() -> new SSOAuthenticationException(SSOErrorCode.SESSION_NOT_FOUND));

            SSOUserGroup userGroup = userGroupRepository.findByProfileId(profileId)
                    .orElseThrow(() -> new SSOAuthenticationException(SSOErrorCode.USER_GROUP_NOT_FOUND));

            List<String> availableRoles = getAvailableRolesForUser(userGroup);

            log.info("[RequestID: {}] Available roles for user: {}", requestId, availableRoles);

            if (!availableRoles.contains(newRole.toUpperCase())) {
                log.error("[RequestID: {}] Requested role '{}' not available. Available roles: {}",
                    requestId, newRole, availableRoles);
                throw new SSOAuthenticationException(SSOErrorCode.REGISTRATION_NOT_FOUND);
            }

            String currentRole = (String) claims.getClaim("current_role");
            if (newRole.toUpperCase().equals(currentRole)) {
                log.warn("[RequestID: {}] User already on role: {}", requestId, newRole);
                throw new SSOAuthenticationException(SSOErrorCode.ROLE_NOT_FOUND);
            }

            Map<String, String> newTokens = generateTokensWithNewRole(
                profileId,
                grpId,
                newRole.toUpperCase(),
                availableRoles,
                userGroup
            );
            log.info("[RequestID: {}] New tokens generated with role: {}", requestId, newRole);

            sessionManagementService.invalidateTokenOnly(currentAccessToken);
            log.info("[RequestID: {}] Old token marked as invalidated", requestId);

            updateSessionForRoleSwitch(session, newRole.toUpperCase(), availableRoles);
            sessionManagementService.storeUserActiveToken(profileId, newTokens.get("accessToken"), true);
            log.info("[RequestID: {}] Session updated with new active token", requestId);

            log.info("[RequestID: {}] Role switch completed successfully to: {}", requestId, newRole);

            return RoleSwitchResponse.builder()
                    .accessToken(newTokens.get("accessToken"))
                    .refreshToken(newTokens.get("refreshToken"))
                    .idToken(session.getSsoIdToken())
                    .currentRole(newRole.toUpperCase())
                    .availableRoles(availableRoles)
                    .profileId(profileId)
                    .grpId(grpId)
                    .tokenType(SSOConstants.TOKEN_TYPE_BEARER)
                    .expiresInSeconds((long) SSOConstants.ACCESS_TOKEN_EXPIRY_SECONDS)
                    .message("Role switched successfully to " + newRole)
                    .build();

        } catch (SSOAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RequestID: {}] Role switch failed", requestId, e);
            throw new SSOAuthenticationException(SSOErrorCode.ROLE_SWITCH_FAILED);
        }
    }

    private List<String> getAvailableRolesForUser(SSOUserGroup userGroup) {
        List<Map<String, Object>> registrationMaps = parseRegistrationsToMaps(userGroup.getRegistrations());

        return userRoleMappingService.getAvailableRoleStrings(
            userGroup.getEmail(),
            registrationMaps
        );
    }

    private List<Map<String, Object>> parseRegistrationsToMaps(JsonNode registrations) {
        if (registrations == null || !registrations.isArray()) {
            return List.of();
        }

        return registrationService.parseRegistrations(registrations)
                .stream()
                .map(this::registrationDTOToMap)
                .toList();
    }

    private Map<String, Object> registrationDTOToMap(com.nexusiam.application.dto.RegistrationDTO dto) {
        Map<String, Object> map = new HashMap<>();
        map.put("portal_id", dto.getPortalId());
        map.put("role", dto.getRole());
        map.put("status", dto.getStatus());
        map.put("valid_till", dto.getValidTill());
        return map;
    }

    private void updateSessionForRoleSwitch(SSOUserSession session, String newRole, List<String> availableRoles) {

        session.setIsActive(true);
        session.setTokenStatus("ACTIVE");
        session.setLastActivityAt(Instant.now());
        sessionRepository.save(session);

        log.info("Session updated for role switch to '{}'. Session remains active.", newRole);
    }

    private Map<String, String> generateTokensWithNewRole(String profileId, String grpId,
                                                          String newRole, List<String> availableRoles,
                                                          SSOUserGroup userGroup) {
        Map<String, Object> registrationsMap = userGroupDataMapper.buildRegistrationsMap(userGroup);

        String accessToken = customTokenService.generateAccessTokenWithRole(
            profileId,
            grpId,
            newRole,
            availableRoles,
            registrationsMap
        );

        String refreshToken = customTokenService.generateRefreshToken(profileId, grpId);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);

        return tokens;
    }

    private String extractGrpId(JWTClaimsSet claims) {
        try {
            Object gc = claims.getClaim("grp_id");
            return gc != null ? gc.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
