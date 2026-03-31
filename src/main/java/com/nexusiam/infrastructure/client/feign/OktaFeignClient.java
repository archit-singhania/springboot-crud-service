package com.nexusiam.infrastructure.client.feign;

import com.nexusiam.infrastructure.config.oauth.OktaFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = "okta-token-client",
        url = "${okta.oauth2.token-endpoint}",
        configuration = OktaFeignConfig.class
)
public interface OktaFeignClient {

    @PostMapping(
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    Map<String, Object> exchangeAuthCodeForTokens(
            @RequestHeader("X-Request-Id") String requestId,
            @RequestHeader("X-Retry-Count") Integer retryCount,
            @RequestParam("grant_type") String grantType,
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("code_verifier") String codeVerifier
    );

    @PostMapping(
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    Map<String, Object> refreshAccessToken(
            @RequestHeader("X-Request-Id") String requestId,
            @RequestHeader("X-Retry-Count") Integer retryCount,
            @RequestParam("grant_type") String grantType,
            @RequestParam("refresh_token") String refreshToken,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret
    );
}
