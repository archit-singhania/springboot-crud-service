package com.nexusiam.infrastructure.config.feign;

import com.nexusiam.presentation.exception.SSOAuthenticationException;
import com.nexusiam.presentation.exception.SSOTokenExchangeException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OktaErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();

        log.error("Okta API error: method={}, status={}", methodKey, status);

        return switch (status) {
            case 400 -> new SSOTokenExchangeException("Invalid request to Okta - bad parameters");
            case 401 -> new SSOAuthenticationException("Authentication failed with Okta");
            case 403 -> new SSOAuthenticationException("Forbidden - insufficient permissions");
            case 429 -> new SSOTokenExchangeException("Rate limit exceeded for Okta API");
            case 500, 502, 503 -> new SSOTokenExchangeException("Okta service unavailable");
            case 504 -> new SSOTokenExchangeException("Okta gateway timeout");
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
