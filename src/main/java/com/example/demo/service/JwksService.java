package com.example.demo.service;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Date;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwksService {

    private final WebClient oktaWebClient;

    @Value("${okta.oauth2.jwks-uri}")
    private String jwksUri;

    @Value("${okta.oauth2.client-id}")
    private String expectedAudience;

    @Value("${okta.oauth2.issuer}")
    private String expectedIssuer;

    private JWKSet cachedJwks;
    private Instant lastFetchTime;

    // Cache JWKS for 6 hours
    private static final long CACHE_DURATION_SECONDS = 6 * 3600;

    public JWTClaimsSet validateAndParseToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            // Refresh JWKS if needed
            if (shouldRefreshJwks()) {
                fetchJwks();
            }

            RSAKey jwk = (RSAKey) cachedJwks.getKeyByKeyId(signedJWT.getHeader().getKeyID());
            if (jwk == null) {
                log.warn("Key ID not found in cached JWKS, refreshing JWKS");
                fetchJwks();
                jwk = (RSAKey) cachedJwks.getKeyByKeyId(signedJWT.getHeader().getKeyID());
            }

            if (jwk == null) {
                throw new RuntimeException("Invalid token: Matching JWK not found");
            }

            JWSVerifier verifier = new RSASSAVerifier(jwk);
            if (!signedJWT.verify(verifier)) {
                throw new RuntimeException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims);

            log.debug("Token validated successfully for subject: {}", claims.getSubject());
            return claims;

        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            throw new RuntimeException("Invalid or expired token: " + e.getMessage(), e);
        }
    }

    private boolean shouldRefreshJwks() {
        return cachedJwks == null
                || lastFetchTime == null
                || lastFetchTime.plusSeconds(CACHE_DURATION_SECONDS).isBefore(Instant.now());
    }

    private void fetchJwks() {
        try {
            log.info("Fetching JWKS from {}", jwksUri);

            String jwksJson = oktaWebClient.get()
                    .uri(jwksUri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            cachedJwks = JWKSet.parse(jwksJson);
            lastFetchTime = Instant.now();

            log.info("JWKS fetched successfully. Loaded {} keys.", cachedJwks.getKeys().size());
        } catch (Exception e) {
            log.error("Failed to fetch JWKS: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch JWKS from Okta", e);
        }
    }

    private void validateClaims(JWTClaimsSet claims) throws Exception {

        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new RuntimeException("Invalid issuer: " + claims.getIssuer());
        }

        if (!claims.getAudience().contains(expectedAudience)) {
            throw new RuntimeException("Invalid audience: " + claims.getAudience());
        }

        Date exp = claims.getExpirationTime();
        if (exp == null || exp.before(new Date())) {
            throw new RuntimeException("Token expired at: " + exp);
        }

        Date iat = claims.getIssueTime();
        if (iat != null && iat.after(new Date())) {
            throw new RuntimeException("Token issue time is in the future: " + iat);
        }

        log.debug("All claims validated successfully");
    }
}
