package com.example.demo.security;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.equals("/auth/login") ||
                path.equals("/auth/register") ||
                path.equals("/") ||
                path.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = extractToken(request.getHeader("Authorization"));
        String refreshToken = request.getHeader("X-Refresh-Token");

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = null;

            try {
                email = jwtTokenUtil.extractEmail(accessToken);
            } catch (ExpiredJwtException e) {
                email = e.getClaims().getSubject();
            }

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails user = userDetailsService.loadUserByUsername(email);

                boolean isTokenValid = jwtTokenUtil.isTokenValid(accessToken);
                boolean isAccessTokenType = jwtTokenUtil.isAccessToken(accessToken);

                if (isTokenValid && isAccessTokenType) {
                    authenticateUser(user, request);
                } else {
                    if (refreshToken != null) {
                        boolean isRefreshValid = jwtTokenUtil.isTokenValid(refreshToken);
                        boolean isRefreshTokenType = jwtTokenUtil.isRefreshToken(refreshToken);

                        if (isRefreshValid && isRefreshTokenType) {
                            String newAccessToken = jwtTokenUtil.generateAccessToken(email);
                            response.setHeader("X-New-Access-Token", newAccessToken);
                            authenticateUser(user, request);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Token validation failed
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(String header) {
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