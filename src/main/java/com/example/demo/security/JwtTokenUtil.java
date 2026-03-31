package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenUtil {

    private static final long ACCESS_TOKEN_VALIDITY_SECONDS = 15 * 60;
    private static final long REFRESH_TOKEN_VALIDITY_SECONDS = 24 * 60 * 60;
    private static final long ID_TOKEN_VALIDITY_SECONDS = 15 * 60;

    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);

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

    public String generateAccessToken(String profileId, String orgId, String roles) {
        Map<String, Object> claims = Map.of(
                "tokenType", "ACCESS",
                "org_id", orgId,
                "roles", roles
        );
        return generateToken(claims, profileId, ACCESS_TOKEN_VALIDITY_SECONDS);
    }

    public String generateRefreshToken(String profileId, String orgId, String roles) {
        Map<String, Object> claims = Map.of(
                "tokenType", "REFRESH",
                "org_id", orgId,
                "roles", roles
        );
        return generateToken(claims, profileId, REFRESH_TOKEN_VALIDITY_SECONDS);
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

    public boolean isIdToken(String token) {
        return isTokenType(token, "ID");
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

    public String getOrgId(String token) {
        return parseToken(token).get("org_id", String.class);
    }

    public String getRoles(String token) {
        return parseToken(token).get("roles", String.class);
    }

    public String extractEmail(String token) {
        return getSubject(token);
    }

    public String generateAccessToken(String email) {
        return generateAccessToken(email, null, "USER");
    }

    public String generateRefreshToken(String email) {
        return generateRefreshToken(email, null, "USER");
    }
}
