package com.nexusiam.infrastructure.config.security;

import com.nexusiam.infrastructure.security.filter.JwtAuthenticationFilter;
import com.nexusiam.infrastructure.security.filter.PermissionAuthorizationFilter;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final PermissionAuthorizationFilter permissionAuthorizationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/exchange/v1/int/auth/login",
                                "/exchange/v1/int/auth/register",
                                "/exchange/v1/int/auth/validate",
                                "/",
                                "/error",
                                "/error/**"
                        ).permitAll()

                        .requestMatchers(
                                "/exchange/v1/int/auth/refresh",
                                "/exchange/v1/sso/refresh"
                        ).authenticated()

                        .requestMatchers(
                                "/exchange/v1/sso/authorize",
                                "/exchange/v1/sso/callback",
                                "/exchange/v1/sso/introspect",
                                "/exchange/v1/sso/validate"
                        ).permitAll()

                        .requestMatchers(
                                "/exchange/v1/sso/profile",
                                "/exchange/v1/sso/profile/detailed",
                                "/exchange/v1/sso/refresh",
                                "/exchange/v1/sso/logout"
                        ).authenticated()

                        .requestMatchers(
                                "/exchange/v1/sso/kyc",
                                "/exchange/v1/sso/kyc/**"
                        ).authenticated()

                        .requestMatchers(
                                "/exchange/v1/int/auth/profile",
                                "/exchange/v1/int/auth/logout",
                                "/exchange/v1/int/users/**"
                        ).authenticated()

                        .requestMatchers(
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/v3/api-docs/**",
                            "/api-docs/**",
                            "/swagger-resources/**",
                            "/webjars/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String acceptHeader = request.getHeader("Accept");

                            if (acceptHeader != null && acceptHeader.contains("text/html")) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("text/html");
                                response.getWriter().write(
                                        "<!DOCTYPE html>" +
                                                "<html>" +
                                                "<head><meta charset='UTF-8'><title>Error 401</title>" +
                                                "<style>body { font-family: Arial, sans-serif; background: #f4f4f4; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }" +
                                                ".container { background: white; padding: 40px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; }" +
                                                "h1 { color: #e74c3c; font-size: 48px; margin: 0; }" +
                                                "p { color: #555; font-size: 18px; }</style></head>" +
                                                "<body><div class='container'><h1>401</h1><p>Unauthorized! Please login.</p></div></body></html>"
                                );
                            } else {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.setCharacterEncoding("UTF-8");
                                response.getWriter().write(
                                        "{\"status\":\"error\",\"message\":\"Unauthorized\",\"statusCode\":401}"
                                );
                                response.getWriter().flush();
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String acceptHeader = request.getHeader("Accept");

                            if (acceptHeader != null && acceptHeader.contains("text/html")) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("text/html");
                                response.getWriter().write(
                                        "<!DOCTYPE html>" +
                                                "<html>" +
                                                "<head><meta charset='UTF-8'><title>Error 403</title>" +
                                                "<style>body { font-family: Arial, sans-serif; background: #f4f4f4; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }" +
                                                ".container { background: white; padding: 40px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; }" +
                                                "h1 { color: #e74c3c; font-size: 48px; margin: 0; }" +
                                                "p { color: #555; font-size: 18px; }</style></head>" +
                                                "<body><div class='container'><h1>403</h1><p>Forbidden! You don't have access.</p></div></body></html>"
                                );
                            } else {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.setCharacterEncoding("UTF-8");
                                response.getWriter().write(
                                        "{\"status\":\"error\",\"message\":\"Access Denied\",\"statusCode\":403}"
                                );
                                response.getWriter().flush();
                            }
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(permissionAuthorizationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "X-New-Access-Token",
                "X-New-Refresh-Token",
                "Authorization",
                "X-Refresh-Token"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RequestIdFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    private static class RequestIdFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                @NonNull HttpServletRequest request,
                @NonNull HttpServletResponse response,
                @NonNull FilterChain filterChain
        ) throws ServletException, IOException {

            String requestId = request.getHeader("X-Request-ID");
            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }

            response.setHeader("X-Request-ID", requestId);
            MDC.put("requestId", requestId);

            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.remove("requestId");
            }
        }
    }
}
