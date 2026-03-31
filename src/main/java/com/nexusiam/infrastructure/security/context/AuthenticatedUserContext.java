package com.nexusiam.infrastructure.security.context;

import com.nexusiam.presentation.exception.SSOException;
import com.nexusiam.infrastructure.util.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticatedUserContext {

    private final JwtTokenUtil jwtTokenUtil;

    public String getAuthenticatedGrpId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                log.error("[Auth Context] No authentication found in SecurityContext");
                throw new SSOException("User not authenticated. Please login.");
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof String && "anonymousUser".equals(principal)) {
                log.error("[Auth Context] Anonymous user detected");
                throw new SSOException("User not authenticated. Please login.");
            }

            Object credentials = authentication.getCredentials();
            if (credentials instanceof String) {
                String token = (String) credentials;

                try {
                    Claims claims = jwtTokenUtil.parseToken(token);

                    String grpId = claims.get("grp_id", String.class);
                    if (grpId == null) {
                        grpId = claims.get("grpId", String.class);
                    }
                    if (grpId == null) {
                        grpId = claims.get("group_id", String.class);
                    }

                    if (grpId != null && !grpId.trim().isEmpty()) {
                        log.info("[Auth Context] Extracted grpId from JWT token: {}", grpId);
                        return grpId;
                    }

                    log.warn("[Auth Context] No grpId found in JWT token claims");
                } catch (Exception e) {
                    log.error("[Auth Context] Failed to parse JWT token: {}", e.getMessage());
                }
            }

            if (principal instanceof ExchangeUserDetails) {
                ExchangeUserDetails userDetails = (ExchangeUserDetails) principal;
                String grpId = userDetails.getGrpId();
                if (grpId != null && !grpId.trim().isEmpty()) {
                    log.info("[Auth Context] Extracted grpId from ExchangeUserDetails: {}", grpId);
                    return grpId;
                }
            }

            String username = authentication.getName();
            if (username != null && !username.trim().isEmpty() && !"anonymousUser".equals(username)) {
                log.info("[Auth Context] Using username as grpId: {}", username);
                return username;
            }

            log.error("[Auth Context] Unable to extract grpId from any source");
            throw new SSOException("Unable to identify user group from authentication token.");

        } catch (SSOException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Auth Context] Error extracting grpId: {}", e.getMessage(), e);
            throw new SSOException("Authentication error. Please re-login.");
        }
    }

    public String getAuthenticatedEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                throw new SSOException("User not authenticated.");
            }

            Object credentials = authentication.getCredentials();
            if (credentials instanceof String) {
                String token = (String) credentials;
                try {
                    Claims claims = jwtTokenUtil.parseToken(token);
                    String email = claims.get("email", String.class);
                    if (email != null) {
                        return email;
                    }
                } catch (Exception e) {
                    log.error("Failed to extract email from token: {}", e.getMessage());
                }
            }

            String name = authentication.getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }

            throw new SSOException("Unable to identify user email.");

        } catch (SSOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting email: {}", e.getMessage());
            throw new SSOException("Authentication error. Please re-login.");
        }
    }

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
            && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal());
    }

    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public Object getRegistrationsFromToken() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                log.error("[Auth Context] No authentication found when extracting registrations");
                return null;
            }

            Object credentials = authentication.getCredentials();
            if (credentials instanceof String) {
                String token = (String) credentials;

                try {
                    Claims claims = jwtTokenUtil.parseToken(token);

                    Object registrations = claims.get("registrations");

                    if (registrations != null) {
                        log.debug("[Auth Context] Extracted registrations from JWT token");
                        return registrations;
                    }

                    log.debug("[Auth Context] No registrations found in JWT token claims");
                } catch (Exception e) {
                    log.error("[Auth Context] Failed to parse JWT token for registrations: {}", e.getMessage());
                }
            }

            return null;

        } catch (Exception e) {
            log.error("[Auth Context] Error extracting registrations: {}", e.getMessage(), e);
            return null;
        }
    }
}
