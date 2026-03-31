package com.nexusiam.application.service.authentication;

import com.nexusiam.application.service.strategy.TokenExchangeStrategy;
import com.nexusiam.infrastructure.adapter.external.OktaAdapter;
import com.nexusiam.infrastructure.config.oauth.OktaOAuth2Config;
import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.shared.constants.SSOErrorCode;
import com.nexusiam.presentation.exception.SSOTokenExchangeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("oktaTokenExchangeServiceFeign")
@Primary
@Slf4j
@RequiredArgsConstructor
public class OktaTokenExchangeServiceFeign implements TokenExchangeStrategy {

    private final OktaAdapter oktaAdapter;
    private final OktaOAuth2Config oktaConfig;

    @Override
    public String getProviderName() {
        return "Okta-Feign";
    }

    @Override
    public Map<String, Object> exchangeAuthCodeForTokens(String authCode, String codeVerifier, String requestId) {
        log.info("[RequestID: {}] Starting Okta auth code exchange with Feign adapter", requestId);
        log.debug("[RequestID: {}] Auth code present: {}, Code verifier present: {}",
                  requestId, authCode != null, codeVerifier != null);

        try {
            Map<String, Object> tokenResponse = oktaAdapter.exchangeAuthCodeForTokens(
                    SSOConstants.GRANT_TYPE_AUTH_CODE,
                    authCode,
                    oktaConfig.getRedirectUri(),
                    oktaConfig.getClientId(),
                    oktaConfig.getClientSecret(),
                    codeVerifier
            );

            validateTokenResponse(tokenResponse, requestId);

            log.info("[RequestID: {}] Okta auth code exchange completed successfully", requestId);
            return tokenResponse;

        } catch (SSOTokenExchangeException e) {
            log.error("[RequestID: {}] SSO Token Exchange failed: {}", requestId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[RequestID: {}] Unexpected error during auth code exchange: {}", requestId, e.getMessage(), e);
            SSOTokenExchangeException ex = new SSOTokenExchangeException("Unexpected error during auth code exchange", e);
            ex.setRequestId(requestId);
            throw ex;
        } finally {
            oktaAdapter.resetRetryCounter();
        }
    }

    @Override
    public Map<String, Object> refreshTokens(String refreshToken, String requestId) {
        log.info("[RequestID: {}] Starting Okta token refresh with Feign adapter", requestId);

        try {
            Map<String, Object> tokenResponse = oktaAdapter.refreshAccessToken(
                    SSOConstants.GRANT_TYPE_REFRESH,
                    refreshToken,
                    oktaConfig.getClientId(),
                    oktaConfig.getClientSecret()
            );

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                SSOTokenExchangeException ex = new SSOTokenExchangeException(SSOErrorCode.REFRESH_TOKEN_INVALID);
                ex.setRequestId(requestId);
                throw ex;
            }

            log.info("[RequestID: {}] Okta token refreshed successfully", requestId);
            return tokenResponse;

        } catch (SSOTokenExchangeException e) {
            log.error("[RequestID: {}] Token refresh failed: {}", requestId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[RequestID: {}] Unexpected error during token refresh: {}", requestId, e.getMessage(), e);
            SSOTokenExchangeException ex = new SSOTokenExchangeException("Unexpected error during token refresh", e);
            ex.setRequestId(requestId);
            throw ex;
        } finally {
            oktaAdapter.resetRetryCounter();
        }
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
}
