package com.example.demo.security;

import com.example.demo.service.CustomTokenService;
import com.nimbusds.jwt.JWTClaimsSet;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final CustomTokenService customTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);
        String refreshToken = request.getHeader("X-Refresh-Token");

        try {
            if (token != null) {
                if (customTokenService.isCustomToken(token)) {
                    log.debug("Processing custom Exchange token");
                    JWTClaimsSet claims = customTokenService.validateAndParseToken(token);

                    String profileId = claims.getSubject();
                    String orgId = (String) claims.getClaim("org_id");
                    Map<String, Object> registrations = (Map<String, Object>) claims.getClaim("registrations");

                    ExchangeUserDetails userDetails = ExchangeUserDetails.builder()
                            .profileId(profileId)
                            .orgId(orgId)
                            .registrations(registrations)
                            .build();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Custom token authenticated for: {}", profileId);
                }
                else {
                    String email = null;
                    try {
                        email = jwtTokenUtil.extractEmail(token);
                    } catch (ExpiredJwtException e) {
                        email = e.getClaims().getSubject();
                    }

                    if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails user = customUserDetailsService.loadUserByUsername(email);

                        if (jwtTokenUtil.isTokenValid(token) && jwtTokenUtil.isAccessToken(token)) {
                            authenticateUser(user, request);
                        } else if (refreshToken != null &&
                                jwtTokenUtil.isTokenValid(refreshToken) &&
                                jwtTokenUtil.isRefreshToken(refreshToken)) {

                            String newAccessToken = jwtTokenUtil.generateAccessToken(email);
                            response.setHeader("X-New-Access-Token", newAccessToken);
                            authenticateUser(user, request);
                        }
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

    private void authenticateUser(UserDetails user, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}

@lombok.Data
@lombok.Builder
class ExchangeUserDetails {
    private String profileId;
    private String orgId;
    private Map<String, Object> registrations;
}
