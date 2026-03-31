package com.nexusiam.infrastructure.security.filter;

import com.nexusiam.application.service.token.CustomTokenService;
import com.nexusiam.infrastructure.security.context.CustomUserDetailsService;
import com.nexusiam.infrastructure.security.context.ExchangeUserDetails;
import com.nexusiam.application.service.session.SessionManagementService;
import com.nexusiam.infrastructure.util.JwtTokenUtil;
import com.nimbusds.jwt.JWTClaimsSet;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final CustomTokenService customTokenService;
    private final JwtTokenUtil jwtTokenUtil;
    private final SessionManagementService sessionManagementService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        if (requestURI.equals("/exchange/v1/int/auth/refresh") ||
            requestURI.equals("/exchange/v1/sso/refresh")) {
            handleRefreshEndpoint(request, response, filterChain);
            return;
        }

        String token = extractToken(request);

        try {
            if (token != null) {
                if (customTokenService.isCustomToken(token)) {
                    JWTClaimsSet claims = customTokenService.validateAndParseToken(token);
                    String profileId = claims.getSubject();

                    if (!sessionManagementService.validateSSOSession(token)) {
                        log.warn("Invalid SSO session detected for profileId: {}", profileId);
                        SecurityContextHolder.clearContext();
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Session invalidated\"}");
                        return;
                    }

                    log.debug("Processing SSO Exchange token for profileId: {}", profileId);

                    String grpId = (String) claims.getClaim("grp_id");
                    Map<String, Object> registrations = claims.getJSONObjectClaim("registrations");
                    String currentRole = (String) claims.getClaim("current_role");
                    Object availableRolesObj = claims.getClaim("available_roles");

                    Collection<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();

                    if (currentRole != null && !currentRole.isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority(currentRole.toUpperCase()));
                        log.debug("Added SSO authority from current_role: {}", currentRole.toUpperCase());
                    }

                    else if (availableRolesObj instanceof java.util.List<?>) {
                        java.util.List<?> availableRolesList = (java.util.List<?>) availableRolesObj;
                        if (!availableRolesList.isEmpty() && availableRolesList.get(0) instanceof String) {
                            String firstRole = (String) availableRolesList.get(0);
                            authorities.add(new SimpleGrantedAuthority(firstRole.toUpperCase()));
                            log.debug("Added SSO authority from available_roles array (fallback): {}", firstRole.toUpperCase());
                        } else {
                            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                            log.warn("Empty or invalid available_roles array in SSO token, defaulting to ROLE_USER");
                        }
                    } else {
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        log.warn("No current_role or available_roles found in SSO token, defaulting to ROLE_USER");
                    }

                    ExchangeUserDetails userDetails = ExchangeUserDetails.builder()
                            .profileId(profileId)
                            .grpId(grpId)
                            .registrations(registrations)
                            .currentRole(currentRole)
                            .roles(availableRolesObj)
                            .build();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    authorities
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("SSO token authenticated for profileId: {} with authority: {}",
                            profileId, currentRole);
                }
                else {
                    log.debug("Processing internal user JWT token");

                    if (jwtTokenUtil.isTokenValid(token) && jwtTokenUtil.isAccessToken(token)) {
                        String email = jwtTokenUtil.extractEmail(token);

                        log.debug("Internal token validated for email: {}", email);

                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("Internal JWT authenticated for email: {} with authorities: {}",
                                email, userDetails.getAuthorities());
                    } else {
                        log.warn("Token validation failed - invalid or not an access token");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void handleRefreshEndpoint(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
            String body = cachedRequest.getBody();

            log.debug("Refresh endpoint - Request body: {}", body);

            String refreshToken = null;
            if (body.contains("refreshToken")) {
                int keyIndex = body.indexOf("refreshToken");
                int colonIndex = body.indexOf(":", keyIndex);
                int quoteStart = body.indexOf("\"", colonIndex);
                int quoteEnd = body.indexOf("\"", quoteStart + 1);

                if (quoteStart > 0 && quoteEnd > quoteStart) {
                    refreshToken = body.substring(quoteStart + 1, quoteEnd);
                }
            }

            log.debug("Extracted refresh token: {}", refreshToken != null ? "Present" : "Null");

            String requestURI = request.getRequestURI();
            boolean isSSORefresh = requestURI.equals("/exchange/v1/sso/refresh");
            log.debug("Is SSO refresh: {}", isSSORefresh);

            if (refreshToken != null && !refreshToken.isEmpty()) {
                try {
                    if (isSSORefresh) {

                        if (customTokenService.isCustomToken(refreshToken)) {
                            JWTClaimsSet claims = customTokenService.validateAndParseToken(refreshToken);
                            String profileId = claims.getSubject();
                            String currentRole = (String) claims.getClaim("current_role");

                            log.debug("SSO refresh token valid for profileId: {}, role: {}", profileId, currentRole);

                            Collection<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                            if (currentRole != null && !currentRole.isEmpty()) {
                                authorities.add(new SimpleGrantedAuthority(currentRole.toUpperCase()));
                            } else {
                                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                            }

                            ExchangeUserDetails userDetails = ExchangeUserDetails.builder()
                                    .profileId(profileId)
                                    .currentRole(currentRole)
                                    .build();

                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails,
                                            null,
                                            authorities
                                    );

                            authentication.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(cachedRequest));

                            SecurityContextHolder.getContext().setAuthentication(authentication);

                            log.info("SSO refresh token authenticated successfully for profileId: {}", profileId);
                        } else {
                            log.warn("Not a valid SSO custom token");
                        }
                    } else {

                        if (jwtTokenUtil.isTokenValid(refreshToken) && jwtTokenUtil.isRefreshToken(refreshToken)) {

                        String email = jwtTokenUtil.extractEmail(refreshToken);
                        String role = jwtTokenUtil.getRole(refreshToken);

                        log.debug("Refresh token valid for email: {}, role: {}", email, role);

                        Collection<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority(role));

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        authorities
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(cachedRequest));

                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.info("Internal refresh token authenticated successfully for email: {}", email);
                    } else {
                        log.warn("Internal refresh token validation failed - invalid or not a refresh token");
                    }
                    }
                } catch (Exception e) {
                    log.error("Error validating refresh token: {}", e.getMessage());
                }
            } else {
                log.warn("No refresh token found in request body");
            }

            filterChain.doFilter(cachedRequest, response);

        } catch (Exception e) {
            log.error("Refresh token validation failed: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
        }
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final String body;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader bufferedReader = request.getReader()) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
            }
            this.body = stringBuilder.toString();
        }

        @Override
        public ServletInputStream getInputStream() {
            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(this.getInputStream()));
        }

        public String getBody() {
            return this.body;
        }
    }
}
