package com.nexusiam.infrastructure.security.checker;

import com.nexusiam.infrastructure.security.context.ExchangeUserDetails;
import com.nexusiam.core.domain.entity.InternalUser;
import com.nexusiam.core.domain.entity.UserApiMapping;
import com.nexusiam.core.domain.entity.ApiSource;
import com.nexusiam.core.domain.repository.UserApiMappingRepository;
import com.nexusiam.core.domain.repository.InternalUserRepository;
import com.nexusiam.core.domain.repository.ApiSourceRepository;
import com.nexusiam.application.service.authorization.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component("permissionChecker")
@RequiredArgsConstructor
public class PermissionChecker {

    private final UserApiMappingRepository mappingRepository;
    private final InternalUserRepository userRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final PermissionCacheService cacheService;

    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authentication found or user not authenticated");
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean hasRole = authorities.stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role)
                                    || authority.getAuthority().equals(role));

        log.debug("Role check for '{}': {}", role, hasRole);
        return hasRole;
    }

    public boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasApiPermission(Long userId, String apiPath, String httpMethod) {

        if (userId == null) {
            log.debug("No userId provided (likely SSO user), allowing access");
            return true;
        }

        try {

            boolean hasPermission = cacheService.hasPermission(userId, apiPath, httpMethod);
            log.debug("API Permission check (CACHED) - User: {}, Path: {}, Method: {} -> {}",
                     userId, apiPath, httpMethod, hasPermission);
            return hasPermission;
        } catch (Exception e) {
            log.error("Error checking API permission for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    public boolean hasApiPermission(String apiPath, String httpMethod) {
        Long userId = getCurrentUserId();

        if (userId == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof ExchangeUserDetails) {
                log.debug("SSO user detected, allowing access (token-based permissions)");
                return true;
            }
            log.debug("No authenticated user found for API permission check");
            return false;
        }
        return hasApiPermission(userId, apiPath, httpMethod);
    }

    public boolean hasAnyAccessToApi(Long userId, String apiPath) {

        if (userId == null) {
            log.debug("Null userId (SSO user) - allowing access");
            return true;
        }

        try {

            List<UserApiMapping> mappings = mappingRepository.findUserAccessibleApisWithDetails(userId);
            return mappings.stream()
                    .anyMatch(mapping -> {
                        ApiSource apiSource = apiSourceRepository.findById(mapping.getApiSourceId()).orElse(null);
                        return apiSource != null && apiPath.equals(apiSource.getApiPath());
                    });
        } catch (Exception e) {
            log.error("Error checking API access for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    public List<String> getAccessibleApiPaths() {
        Long userId = getCurrentUserId();

        if (userId == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof ExchangeUserDetails) {
                log.debug("SSO user - returning empty accessible API paths list");
                return List.of();
            }
            return List.of();
        }

        List<UserApiMapping> mappings = mappingRepository.findUserAccessibleApisWithDetails(userId);
        return mappings.stream()
                .map(mapping -> apiSourceRepository.findById(mapping.getApiSourceId()).orElse(null))
                .filter(apiSource -> apiSource != null)
                .map(apiSource -> apiSource.getApiPath())
                .distinct()
                .collect(Collectors.toList());
    }

    public boolean hasApiPermissionWithFallback(Long userId, String apiPath, String httpMethod, String... fallbackRoles) {

        if (hasApiPermission(userId, apiPath, httpMethod)) {
            return true;
        }

        return hasAnyRole(fallbackRoles);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        try {

            Object principal = authentication.getPrincipal();

            if (principal instanceof ExchangeUserDetails) {
                log.debug("SSO user detected - permission checks not applicable for SSO users");
                return null;
            }

            if (principal instanceof org.springframework.security.core.userdetails.User) {
                String email = ((org.springframework.security.core.userdetails.User) principal).getUsername();
                log.debug("Extracted email from principal: {}", email);

                Optional<InternalUser> userOpt = userRepository.findByEmailAndIsDeletedFalse(email);
                if (userOpt.isPresent()) {
                    Long userId = userOpt.get().getId();
                    log.debug("Found user ID {} for email {}", userId, email);
                    return userId;
                } else {
                    log.warn("User not found for email: {}", email);
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting user ID from authentication: {}", e.getMessage());
            return null;
        }
    }

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.User) {
            return ((org.springframework.security.core.userdetails.User) principal).getUsername();
        }
        return null;
    }

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
