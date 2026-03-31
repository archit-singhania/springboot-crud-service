package com.nexusiam.infrastructure.adapter.external;

import com.nexusiam.infrastructure.config.oauth.OktaRequestInterceptor;
import com.nexusiam.shared.constants.SSOErrorCode;
import com.nexusiam.presentation.exception.SSOTokenExchangeException;
import com.nexusiam.infrastructure.client.feign.OktaFeignClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class OktaAdapter {

    private final OktaFeignClient oktaFeignClient;
    private final ThreadLocal<AtomicInteger> retryCounter = ThreadLocal.withInitial(() -> new AtomicInteger(0));

    @CircuitBreaker(name = "oktaTokenExchange", fallbackMethod = "exchangeTokenFallback")
    @Retry(name = "oktaTokenExchange")
    public Map<String, Object> exchangeAuthCodeForTokens(
            String grantType,
            String code,
            String redirectUri,
            String clientId,
            String clientSecret,
            String codeVerifier) {
        try {
            int currentRetry = retryCounter.get().getAndIncrement();
            OktaRequestInterceptor.setRetryCount(currentRetry);

            String requestId = UUID.randomUUID().toString();

            log.info("Exchanging auth code with Okta: code={}, retry={}",
                    maskSensitiveData(code), currentRetry);

            Map<String, Object> response = oktaFeignClient.exchangeAuthCodeForTokens(
                    requestId,
                    currentRetry,
                    grantType,
                    code,
                    redirectUri,
                    clientId,
                    clientSecret,
                    codeVerifier
            );

            log.info("Okta token exchange successful: requestId={}, retry={}", requestId, currentRetry);
            return response;

        } finally {
            OktaRequestInterceptor.clearRetryCount();
        }
    }

    @CircuitBreaker(name = "oktaTokenRefresh", fallbackMethod = "refreshTokenFallback")
    @Retry(name = "oktaTokenRefresh")
    public Map<String, Object> refreshAccessToken(
            String grantType,
            String refreshToken,
            String clientId,
            String clientSecret) {
        try {
            int currentRetry = retryCounter.get().getAndIncrement();
            OktaRequestInterceptor.setRetryCount(currentRetry);

            String requestId = UUID.randomUUID().toString();

            log.info("Refreshing access token with Okta: retry={}", currentRetry);

            Map<String, Object> response = oktaFeignClient.refreshAccessToken(
                    requestId,
                    currentRetry,
                    grantType,
                    refreshToken,
                    clientId,
                    clientSecret
            );

            log.info("Okta token refresh successful: requestId={}, retry={}", requestId, currentRetry);
            return response;

        } finally {
            OktaRequestInterceptor.clearRetryCount();
        }
    }

    public void resetRetryCounter() {
        retryCounter.get().set(0);
    }

    public Map<String, Object> exchangeTokenFallback(
            String grantType,
            String code,
            String redirectUri,
            String clientId,
            String clientSecret,
            String codeVerifier,
            Exception e) {

        log.error("Okta token exchange failed after {} retries: code={}",
                retryCounter.get().get(), maskSensitiveData(code), e);

        resetRetryCounter();

        throw new SSOTokenExchangeException(
                SSOErrorCode.TOKEN_EXCHANGE_FAILED
        );
    }

    public Map<String, Object> refreshTokenFallback(
            String grantType,
            String refreshToken,
            String clientId,
            String clientSecret,
            Exception e) {

        log.error("Okta token refresh failed after {} retries",
                retryCounter.get().get(), e);

        resetRetryCounter();

        throw new SSOTokenExchangeException(
                SSOErrorCode.TOKEN_EXCHANGE_FAILED
        );
    }

    private String maskSensitiveData(String data) {
        if (data == null || data.length() < 8) {
            return "***";
        }
        return data.substring(0, 4) + "..." + data.substring(data.length() - 4);
    }
}
