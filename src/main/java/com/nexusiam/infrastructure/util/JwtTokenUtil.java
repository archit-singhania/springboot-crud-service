package com.nexusiam.infrastructure.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;

import java.util.Base64;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenUtil {

    private static final long ACCESS_TOKEN_VALIDITY_SECONDS = 15 * 60;
    private static final long REFRESH_TOKEN_VALIDITY_SECONDS = 24 * 60 * 60;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Key key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Map<String, Object> claims, String subject, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public String generateAccessToken(String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "ACCESS");
        claims.put("role", role);
        return generateToken(claims, email, ACCESS_TOKEN_VALIDITY_SECONDS);
    }

    public String generateAccessTokenWithPermissions(
            String email,
            Long userId,
            String role,
            Map<String, List<String>> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "ACCESS");
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("permissions", permissions);
        claims.put("permissionCount", permissions.size());
        return generateToken(claims, email, ACCESS_TOKEN_VALIDITY_SECONDS);
    }

    public String generateAccessTokenWithCompactPermissions(
            String email,
            Long userId,
            String role,
            List<String> compactPermissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "ACCESS");
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("permissions", compactPermissions);
        claims.put("permissionCount", compactPermissions.size());
        return generateToken(claims, email, ACCESS_TOKEN_VALIDITY_SECONDS);
    }

    public String generateRefreshToken(String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "REFRESH");
        claims.put("role", role);
        return generateToken(claims, email, REFRESH_TOKEN_VALIDITY_SECONDS);
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenType(String token, String type) {
        try {
            return type.equals(parseToken(token).get("tokenType", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return isTokenType(token, "ACCESS");
    }

    public boolean isRefreshToken(String token) {
        return isTokenType(token, "REFRESH");
    }

    public boolean isTokenValid(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            return expiration != null && expiration.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String getSubject(String token) {
        return parseToken(token).getSubject();
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    public String extractEmail(String token) {
        return getSubject(token);
    }

    public Long getUserId(String token) {
        try {
            Object userId = parseToken(token).get("userId");
            if (userId instanceof Number) {
                return ((Number) userId).longValue();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> getCompactPermissions(String token) {
        try {
            Object permissions = parseToken(token).get("permissions");
            if (permissions instanceof List<?>) {
                List<?> permList = (List<?>) permissions;
                List<String> result = new java.util.ArrayList<>();
                for (Object perm : permList) {
                    if (perm instanceof String) {
                        result.add((String) perm);
                    }
                }
                return result;
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, List<String>> getPermissionsMap(String token) {
        try {
            Object permissions = parseToken(token).get("permissions");
            if (permissions instanceof Map<?, ?>) {
                Map<?, ?> permMap = (Map<?, ?>) permissions;
                Map<String, List<String>> result = new java.util.HashMap<>();
                for (Map.Entry<?, ?> entry : permMap.entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() instanceof List<?>) {
                        String key = (String) entry.getKey();
                        List<?> valueList = (List<?>) entry.getValue();
                        List<String> methods = new java.util.ArrayList<>();
                        for (Object method : valueList) {
                            if (method instanceof String) {
                                methods.add((String) method);
                            }
                        }
                        result.put(key, methods);
                    }
                }
                return result;
            }
            return Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    public boolean hasPermissionInToken(String token, String apiPath, String httpMethod) {
        try {
            Object permissions = parseToken(token).get("permissions");

            if (permissions instanceof List) {
                List<String> compactPerms = getCompactPermissions(token);
                String permString = httpMethod.toUpperCase() + ":" + apiPath;
                return compactPerms.contains(permString);
            }

            if (permissions instanceof Map) {
                Map<String, List<String>> permMap = getPermissionsMap(token);
                List<String> methods = permMap.get(apiPath);
                return methods != null && methods.contains(httpMethod.toUpperCase());
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public Integer getPermissionCount(String token) {
        try {
            Object count = parseToken(token).get("permissionCount");
            if (count instanceof Number) {
                return ((Number) count).intValue();
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
