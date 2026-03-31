package com.nexusiam.infrastructure.security.filter;

import com.nexusiam.infrastructure.security.context.ExchangeUserDetails;
import com.nexusiam.core.domain.entity.UserApiMapping;
import com.nexusiam.core.domain.repository.UserApiMappingRepository;
import com.nexusiam.infrastructure.util.JwtTokenUtil;
import com.nexusiam.core.domain.repository.ApiSourceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionAuthorizationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserApiMappingRepository mappingRepository;
    private final ApiSourceRepository apiSourceRepository;

    private static final List<String> WHITELIST_PATHS = Arrays.asList(
        "/exchange/v1/int/auth/login",
        "/exchange/v1/int/auth/register",
        "/exchange/v1/int/auth/validate",
        "/exchange/v1/sso/authorize",
        "/exchange/v1/sso/callback",
        "/exchange/v1/sso/introspect",
        "/exchange/v1/sso/validate",
        "/swagger-ui",
        "/v3/api-docs",
        "/actuator",
        "/health",
        "/error"
    );

    private static final List<String> PUBLIC_AUTHENTICATED_PATHS = Arrays.asList(
        "/exchange/v1/int/auth/profile",
        "/exchange/v1/int/auth/logout",
        "/exchange/v1/int/auth/refresh",
        "/exchange/v1/sso/refresh"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String httpMethod = request.getMethod();

        log.debug("PermissionAuthorizationFilter: Checking {} {}", httpMethod, requestPath);

        if (isWhitelisted(requestPath)) {
            log.debug("Path {} is whitelisted, skipping permission check", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        if (isPublicAuthenticatedPath(requestPath)) {
            log.debug("Path {} is public authenticated, skipping permission check", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authentication found, continuing filter chain");
            filterChain.doFilter(request, response);
            return;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof ExchangeUserDetails) {
            log.debug("SSO user detected - skipping permission check (token-based authorization)");
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No Bearer token found, continuing filter chain");
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!hasEmbeddedPermissions(token)) {
            log.debug("Token does not have embedded permissions, falling back to role-based auth");
            filterChain.doFilter(request, response);
            return;
        }

        boolean hasPermission = checkPermission(token, requestPath, httpMethod);

        if (hasPermission) {
            log.debug("Permission granted for {} {} by token", httpMethod, requestPath);
            filterChain.doFilter(request, response);
        } else {

            boolean hasDatabasePermission = checkDatabasePermission(token, requestPath, httpMethod);

            if (hasDatabasePermission) {
                log.debug("Permission granted for {} {} by database fallback", httpMethod, requestPath);
                filterChain.doFilter(request, response);
            } else {
                log.warn("Permission denied for {} {} - user does not have required permission",
                        httpMethod, requestPath);
                sendPermissionDeniedResponse(response, requestPath, httpMethod);
            }
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isPublicAuthenticatedPath(String path) {
        return PUBLIC_AUTHENTICATED_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean hasEmbeddedPermissions(String token) {
        try {
            Integer permissionCount = jwtTokenUtil.getPermissionCount(token);
            return permissionCount != null && permissionCount > 0;
        } catch (Exception e) {
            log.debug("Error checking for embedded permissions: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkPermission(String token, String requestPath, String httpMethod) {
        try {

            if (jwtTokenUtil.hasPermissionInToken(token, requestPath, httpMethod)) {
                return true;
            }

            List<String> compactPermissions = jwtTokenUtil.getCompactPermissions(token);
            return matchesWildcardPermission(compactPermissions, requestPath, httpMethod);

        } catch (Exception e) {
            log.error("Error checking permission from token: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkDatabasePermission(String token, String requestPath, String httpMethod) {
        try {
            Long userId = jwtTokenUtil.getUserId(token);
            if (userId == null) {
                return false;
            }

            if (mappingRepository.hasPermission(userId, requestPath, httpMethod)) {
                return true;
            }

            return checkWildcardDatabasePermission(userId, requestPath, httpMethod);

        } catch (Exception e) {
            log.error("Error checking database permission: {}", e.getMessage());
            return false;
        }
    }

    private boolean matchesWildcardPermission(List<String> permissions, String requestPath, String httpMethod) {
        String permString = httpMethod.toUpperCase() + ":" + requestPath;

        for (String permission : permissions) {

            if (permission.equals(permString)) {
                return true;
            }

            if (matchesPattern(permission, permString)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkWildcardDatabasePermission(Long userId, String requestPath, String httpMethod) {
        try {

            List<UserApiMapping> mappings = mappingRepository.findUserAccessibleApisWithDetails(userId);

            List<String> accessiblePaths = mappings.stream()
                    .map(m -> apiSourceRepository.findById(m.getApiSourceId()).orElse(null))
                    .filter(apiSource -> apiSource != null)
                    .map(apiSource -> apiSource.getApiPath())
                    .distinct()
                    .collect(Collectors.toList());

            for (String apiPath : accessiblePaths) {
                if (matchesPathPattern(apiPath, requestPath)) {

                    return mappingRepository.hasPermission(userId, apiPath, httpMethod);
                }
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking wildcard database permission: {}", e.getMessage());
            return false;
        }
    }

    private boolean matchesPattern(String pattern, String actual) {

        String[] patternParts = pattern.split(":", 2);
        String[] actualParts = actual.split(":", 2);

        if (patternParts.length != 2 || actualParts.length != 2) {
            return false;
        }

        String patternMethod = patternParts[0];
        String patternPath = patternParts[1];
        String actualMethod = actualParts[0];
        String actualPath = actualParts[1];

        if (!patternMethod.equals(actualMethod)) {
            return false;
        }

        return matchesPathPattern(patternPath, actualPath);
    }

    private boolean matchesPathPattern(String pattern, String path) {

        if (pattern.equals(path)) {
            return true;
        }

        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix);
        }

        String[] patternSegments = pattern.split("/");
        String[] pathSegments = path.split("/");

        if (patternSegments.length != pathSegments.length) {
            return false;
        }

        for (int i = 0; i < patternSegments.length; i++) {
            String patternSegment = patternSegments[i];
            String pathSegment = pathSegments[i];

            if (patternSegment.startsWith(":")) {
                continue;
            }

            if (!patternSegment.equals(pathSegment)) {
                return false;
            }
        }

        return true;
    }

    private void sendPermissionDeniedResponse(
            HttpServletResponse response,
            String requestPath,
            String httpMethod) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        String errorMessage = String.format(
            "{\"error\":\"Permission Denied\",\"message\":\"You do not have permission to %s %s\",\"status\":403}",
            httpMethod, requestPath
        );

        response.getWriter().write(errorMessage);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return false;
    }
}
