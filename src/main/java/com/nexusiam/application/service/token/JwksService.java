package com.nexusiam.application.service.token;

import com.nexusiam.infrastructure.config.oauth.OktaOAuth2Config;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwksService {

    private final WebClient oktaWebClient;
    private final OktaOAuth2Config oktaConfig;

    private JWKSet cachedJwks;
    private Instant lastFetchTime;

    private static final long CACHE_DURATION_SECONDS = 6 * 3600;

    public JWTClaimsSet validateAndParseToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (shouldRefreshJwks()) {
                fetchJwks();
            }

            String keyId = signedJWT.getHeader().getKeyID();
            RSAKey jwk = (RSAKey) cachedJwks.getKeyByKeyId(keyId);

            if (jwk == null) {
                log.warn("Key ID {} not found in cached JWKS, refreshing", keyId);
                fetchJwks();
                jwk = (RSAKey) cachedJwks.getKeyByKeyId(keyId);
            }

            if (jwk == null) {
                throw new RuntimeException("Invalid token: Matching JWK not found for key ID: " + keyId);
            }

            JWSVerifier verifier = new RSASSAVerifier(jwk);
            if (!signedJWT.verify(verifier)) {
                throw new RuntimeException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims);

            log.debug("Token validated successfully for subject: {} with key ID: {}",
                    claims.getSubject(), keyId);

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
            String validatedJwksUri = oktaConfig.getJwksUri();
            if (validatedJwksUri == null || validatedJwksUri.isEmpty()) {
                throw new IllegalStateException("JWKS URI is not configured");
            }

            log.info("Fetching JWKS from {}", validatedJwksUri);

            String jwksJson = Objects.requireNonNull(
                oktaWebClient.get()
                    .uri(validatedJwksUri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(),
                "JWKS response cannot be null"
            );

            if (jwksJson == null || jwksJson.isEmpty()) {
                throw new RuntimeException("Empty JWKS response received");
            }

            cachedJwks = JWKSet.parse(jwksJson);
            lastFetchTime = Instant.now();

            log.info("JWKS fetched successfully. Loaded {} keys.", cachedJwks.getKeys().size());
        } catch (Exception e) {
            log.error("Failed to fetch JWKS: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch JWKS from Okta", e);
        }
    }

    private void validateClaims(JWTClaimsSet claims) throws Exception {
        if (oktaConfig.getIssuer() == null || oktaConfig.getIssuer().isEmpty()) {
            throw new IllegalStateException("Expected issuer is not configured");
        }

        if (oktaConfig.getClientId() == null || oktaConfig.getClientId().isEmpty()) {
            throw new IllegalStateException("Expected audience is not configured");
        }

        String actualIssuer = claims.getIssuer();
        if (actualIssuer == null || !oktaConfig.getIssuer().equals(actualIssuer)) {
            throw new RuntimeException("Invalid issuer: " + actualIssuer);
        }

        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(oktaConfig.getClientId())) {
            throw new RuntimeException("Invalid audience: " + audience);
        }

        long clockSkewSeconds = 30;
        Instant now = Instant.now();

        Date exp = claims.getExpirationTime();
        if (exp == null || exp.toInstant().plusSeconds(clockSkewSeconds).isBefore(now)) {
            throw new RuntimeException("Token expired at: " + exp);
        }

        Date iat = claims.getIssueTime();
        if (iat != null && iat.toInstant().isAfter(now.plusSeconds(clockSkewSeconds))) {
            throw new RuntimeException("Token issue time is in the future: " + iat);
        }

        log.debug("All claims validated successfully with clock skew tolerance");
    }
}
