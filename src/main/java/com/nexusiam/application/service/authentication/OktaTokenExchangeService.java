package com.nexusiam.application.service.authentication;

import com.nexusiam.application.service.strategy.TokenExchangeStrategy;
import com.nexusiam.infrastructure.config.oauth.OktaOAuth2Config;
import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.shared.constants.SSOErrorCode;
import com.nexusiam.presentation.exception.SSOConfigurationException;
import com.nexusiam.presentation.exception.SSOTokenExchangeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class OktaTokenExchangeService implements TokenExchangeStrategy {

    private final WebClient oktaWebClient;
    private final OktaOAuth2Config oktaConfig;

    @Override
    public String getProviderName() {
        return "Okta";
    }

    @Override
    public Map<String, Object> exchangeAuthCodeForTokens(String authCode, String codeVerifier, String requestId) {
        log.info("[RequestID: {}] Starting Okta auth code exchange", requestId);

        MultiValueMap<String, String> formData = buildAuthCodeFormData(authCode, codeVerifier);

        String requestBody = formData.toString();
        String requestHash = computeSHA256Hash(requestBody);

        try {
            Map<String, Object> oktaTokenResponse = oktaWebClient.post()
                    .uri(Objects.requireNonNull(validateEndpoint(oktaConfig.getTokenEndpoint(), "Token endpoint")))
                    .header(SSOConstants.HEADER_REQUEST_ID, requestId)
                    .header(SSOConstants.HEADER_REQUEST_HASH, requestHash)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_FORM_URLENCODED))
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("[RequestID: {}] Okta token exchange error: {}", requestId, body);
                                        SSOTokenExchangeException ex = new SSOTokenExchangeException(SSOErrorCode.TOKEN_EXCHANGE_FAILED);
                                        ex.setRequestId(requestId);
                                        return reactor.core.publisher.Mono.error(ex);
                                    })
                    )
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            validateTokenResponse(oktaTokenResponse, requestId);
            log.info("[RequestID: {}] Okta auth code exchange completed successfully", requestId);
            return oktaTokenResponse;

        } catch (SSOTokenExchangeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RequestID: {}] Failed to exchange auth code with Okta", requestId, e);
            SSOTokenExchangeException ex = new SSOTokenExchangeException("Failed to exchange auth code with Okta", e);
            ex.setRequestId(requestId);
            throw ex;
        }
    }

    @Override
    public Map<String, Object> refreshTokens(String refreshToken, String requestId) {
        log.info("[RequestID: {}] Starting Okta token refresh", requestId);

        MultiValueMap<String, String> formData = buildRefreshTokenFormData(refreshToken);

        try {
            Map<String, Object> tokenResponse = oktaWebClient.post()
                    .uri(Objects.requireNonNull(validateEndpoint(oktaConfig.getTokenEndpoint(), "Token endpoint")))
                    .header(SSOConstants.HEADER_REQUEST_ID, requestId)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_FORM_URLENCODED))
                    .body(BodyInserters.fromFormData(Objects.requireNonNull(formData)))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("[RequestID: {}] Okta token refresh error: {}", requestId, body);
                                        SSOTokenExchangeException ex = new SSOTokenExchangeException(SSOErrorCode.REFRESH_TOKEN_INVALID);
                                        ex.setRequestId(requestId);
                                        return reactor.core.publisher.Mono.error(ex);
                                    })
                    )
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                SSOTokenExchangeException ex = new SSOTokenExchangeException(SSOErrorCode.REFRESH_TOKEN_INVALID);
                ex.setRequestId(requestId);
                throw ex;
            }

            log.info("[RequestID: {}] Okta token refreshed successfully", requestId);
            return tokenResponse;

        } catch (SSOTokenExchangeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RequestID: {}] Failed to refresh Okta token", requestId, e);
            SSOTokenExchangeException ex = new SSOTokenExchangeException("Failed to refresh Okta token", e);
            ex.setRequestId(requestId);
            throw ex;
        }
    }

    private MultiValueMap<String, String> buildAuthCodeFormData(String authCode, String codeVerifier) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", SSOConstants.GRANT_TYPE_AUTH_CODE);
        formData.add("code", authCode);
        formData.add("redirect_uri", oktaConfig.getRedirectUri());
        formData.add("client_id", oktaConfig.getClientId());
        formData.add("client_secret", oktaConfig.getClientSecret());
        formData.add("code_verifier", codeVerifier);
        return formData;
    }

    private MultiValueMap<String, String> buildRefreshTokenFormData(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", SSOConstants.GRANT_TYPE_REFRESH);
        formData.add("refresh_token", refreshToken);
        formData.add("client_id", oktaConfig.getClientId());
        formData.add("client_secret", oktaConfig.getClientSecret());
        return formData;
    }

    private void validateTokenResponse(Map<String, Object> response, String requestId) {
        if (response == null ||
            !response.containsKey("access_token") ||
            !response.containsKey("id_token")) {
            log.error("[RequestID: {}] Invalid token response from Okta: missing required tokens", requestId);
            SSOTokenExchangeException ex = new SSOTokenExchangeException(SSOErrorCode.TOKEN_EXCHANGE_FAILED);
            ex.setRequestId(requestId);
            throw ex;
        }
    }

    private String validateEndpoint(String endpoint, String name) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new SSOConfigurationException(SSOErrorCode.CONFIGURATION_ERROR);
        }
        return endpoint;
    }

    private String computeSHA256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to compute SHA-256 hash", e);
            throw new SSOConfigurationException("Failed to compute SHA-256 hash", e);
        }
    }
}
