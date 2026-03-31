package com.example.demo.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class CustomTokenService {

    @Value("${exchange.token.issuer:https://etp.cpcb.gov.in}")
    private String issuer;

    @Value("${exchange.token.expiry-minutes:15}")
    private int accessTokenExpiryMinutes;

    @Value("${exchange.token.refresh-expiry-hours:24}")
    private int refreshTokenExpiryHours;

    private RSAKey rsaKey;
    private JWSSigner signer;
    private JWSVerifier verifier;

    @PostConstruct
    public void init() {
        try {
            rsaKey = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();

            signer = new RSASSASigner(rsaKey);
            verifier = new RSASSAVerifier((RSAPublicKey) rsaKey.toPublicKey());

            log.info("Custom token service initialized with key ID: {}", rsaKey.getKeyID());
        } catch (Exception e) {
            log.error("Failed to initialize custom token service", e);
            throw new RuntimeException("Failed to initialize token service", e);
        }
    }

    public String generateAccessToken(String profileId, String orgId,
                                      Map<String, Object> registrations) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(accessTokenExpiryMinutes * 60L);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(profileId)
                    .audience("ETP_EXCHANGE")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("org_id", orgId)
                    .claim("token_type", "access")
                    .claim("registrations", registrations)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claims);

            signedJWT.sign(signer);

            String token = signedJWT.serialize();
            log.info("Generated custom access token for profileId: {}, orgId: {}", profileId, orgId);

            return token;
        } catch (Exception e) {
            log.error("Failed to generate access token", e);
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    public String generateRefreshToken(String profileId, String orgId) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(refreshTokenExpiryHours * 3600L);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(profileId)
                    .audience("ETP_EXCHANGE")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("org_id", orgId)
                    .claim("token_type", "refresh")
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claims);

            signedJWT.sign(signer);

            String token = signedJWT.serialize();
            log.info("Generated custom refresh token for profileId: {}, orgId: {}", profileId, orgId);

            return token;
        } catch (Exception e) {
            log.error("Failed to generate refresh token", e);
            throw new RuntimeException("Failed to generate refresh token", e);
        }
    }

    public JWTClaimsSet validateAndParseToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new RuntimeException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (!issuer.equals(claims.getIssuer())) {
                throw new RuntimeException("Invalid token issuer");
            }

            if (!claims.getAudience().contains("ETP_EXCHANGE")) {
                throw new RuntimeException("Invalid token audience");
            }

            if (claims.getExpirationTime().before(new Date())) {
                throw new RuntimeException("Token expired");
            }

            log.debug("Token validated successfully for subject: {}", claims.getSubject());
            return claims;

        } catch (Exception e) {
            log.error("Token validation failed", e);
            throw new RuntimeException("Invalid or expired token", e);
        }
    }

    public JWTClaimsSet validateIdToken(String idToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(idToken);

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                throw new RuntimeException("ID token expired");
            }

            return claims;
        } catch (Exception e) {
            log.error("ID token validation failed", e);
            throw new RuntimeException("Invalid or expired ID token", e);
        }
    }

    public String getJWKS() {
        try {
            return rsaKey.toPublicJWK().toJSONString();
        } catch (Exception e) {
            log.error("Failed to export JWKS", e);
            throw new RuntimeException("Failed to export JWKS", e);
        }
    }

    public boolean isCustomToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            boolean issuerMatch = issuer.equals(claims.getIssuer());
            boolean audienceMatch = claims.getAudience().contains("ETP_EXCHANGE");
            boolean hasTokenType = claims.getClaim("token_type") != null;

            return issuerMatch && audienceMatch && hasTokenType;

        } catch (Exception e) {
            return false;
        }
    }
}