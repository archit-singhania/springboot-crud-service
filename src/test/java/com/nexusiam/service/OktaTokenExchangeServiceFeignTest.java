package com.nexusiam.service;

import com.nexusiam.application.service.authentication.OktaTokenExchangeServiceFeign;
import com.nexusiam.infrastructure.config.oauth.OktaOAuth2Config;
import com.nexusiam.presentation.exception.SSOTokenExchangeException;
import com.nexusiam.infrastructure.client.feign.OktaFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OktaTokenExchangeServiceFeign Tests")
class OktaTokenExchangeServiceFeignTest {

    @Mock
    private OktaFeignClient oktaFeignClient;

    @Spy
    private OktaOAuth2Config oktaConfig = createTestConfig();

    @Mock
    private com.nexusiam.infrastructure.adapter.external.OktaAdapter oktaAdapter;

    @InjectMocks
    private OktaTokenExchangeServiceFeign service;

    private static final String TEST_REQUEST_ID = "test-request-123";
    private static final String TEST_AUTH_CODE = "test-auth-code";
    private static final String TEST_CODE_VERIFIER = "test-verifier";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";

    private static OktaOAuth2Config createTestConfig() {
        OktaOAuth2Config config = new OktaOAuth2Config();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setRedirectUri("https://localhost/callback");
        config.setIssuer("https://test.okta.com");
        config.setTokenEndpoint("https://test.okta.com/oauth2/v1/token");
        config.setAuthorizationEndpoint("https://test.okta.com/oauth2/v1/authorize");
        config.setProfileEndpoint("https://test.okta.com/oauth2/v1/userinfo");
        config.setJwksUri("https://test.okta.com/oauth2/v1/keys");
        config.setScope("openid profile email");
        return config;
    }

    @BeforeEach
    void setUp() {
        // Config is already initialized with @Spy and createTestConfig()
        // No need to mock methods as we're using a real instance
    }

    @Test
    @DisplayName("Should successfully exchange auth code for tokens")
    void shouldExchangeAuthCodeSuccessfully() {

        Map<String, Object> expectedResponse = createMockTokenResponse();
        when(oktaAdapter.exchangeAuthCodeForTokens(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(expectedResponse);

        Map<String, Object> result = service.exchangeAuthCodeForTokens(
                TEST_AUTH_CODE,
                TEST_CODE_VERIFIER,
                TEST_REQUEST_ID
        );

        assertThat(result).isNotNull();
        assertThat(result).containsKeys("access_token", "id_token", "refresh_token");
        assertThat(result.get("access_token")).isEqualTo("mock-access-token");

        verify(oktaAdapter, times(1)).exchangeAuthCodeForTokens(
                anyString(),
                eq(TEST_AUTH_CODE),
                anyString(),
                anyString(),
                anyString(),
                eq(TEST_CODE_VERIFIER)
        );
        verify(oktaAdapter, times(1)).resetRetryCounter();
    }

    @Test
    @DisplayName("Should successfully refresh tokens")
    void shouldRefreshTokensSuccessfully() {

        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("access_token", "new-access-token");
        expectedResponse.put("expires_in", 3600);

        when(oktaAdapter.refreshAccessToken(
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(expectedResponse);

        Map<String, Object> result = service.refreshTokens(TEST_REFRESH_TOKEN, TEST_REQUEST_ID);

        assertThat(result).isNotNull();
        assertThat(result).containsKey("access_token");
        assertThat(result.get("access_token")).isEqualTo("new-access-token");

        verify(oktaAdapter, times(1)).refreshAccessToken(
                anyString(),
                eq(TEST_REFRESH_TOKEN),
                anyString(),
                anyString()
        );
        verify(oktaAdapter, times(1)).resetRetryCounter();
    }

    @Test
    @DisplayName("Should throw exception when token response is invalid")
    void shouldThrowExceptionWhenTokenResponseInvalid() {

        Map<String, Object> invalidResponse = new HashMap<>();
        invalidResponse.put("access_token", "token");

        when(oktaAdapter.exchangeAuthCodeForTokens(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(invalidResponse);

        assertThatThrownBy(() -> service.exchangeAuthCodeForTokens(
                TEST_AUTH_CODE,
                TEST_CODE_VERIFIER,
                TEST_REQUEST_ID
        ))
                .isInstanceOf(SSOTokenExchangeException.class);
    }

    @Test
    @DisplayName("Should handle Feign exception and wrap in domain exception")
    void shouldHandleFeignException() {

        when(oktaAdapter.exchangeAuthCodeForTokens(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenThrow(new SSOTokenExchangeException("Okta service error"));

        assertThatThrownBy(() -> service.exchangeAuthCodeForTokens(
                TEST_AUTH_CODE,
                TEST_CODE_VERIFIER,
                TEST_REQUEST_ID
        ))
                .isInstanceOf(SSOTokenExchangeException.class);
    }

    @Test
    @DisplayName("Should throw exception when refresh token response is invalid")
    void shouldThrowExceptionWhenRefreshTokenResponseInvalid() {

        when(oktaAdapter.refreshAccessToken(
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(null);

        assertThatThrownBy(() -> service.refreshTokens(
                TEST_REFRESH_TOKEN,
                TEST_REQUEST_ID
        ))
                .isInstanceOf(SSOTokenExchangeException.class);
    }

    @Test
    @DisplayName("Should throw exception when refresh token response missing access token")
    void shouldThrowExceptionWhenAccessTokenMissing() {

        Map<String, Object> invalidResponse = new HashMap<>();
        invalidResponse.put("expires_in", 3600);

        when(oktaAdapter.refreshAccessToken(
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(invalidResponse);

        assertThatThrownBy(() -> service.refreshTokens(
                TEST_REFRESH_TOKEN,
                TEST_REQUEST_ID
        ))
                .isInstanceOf(SSOTokenExchangeException.class);
    }

    @Test
    @DisplayName("Should verify provider name is Okta-Feign")
    void shouldReturnCorrectProviderName() {

        String providerName = service.getProviderName();

        assertThat(providerName).isEqualTo("Okta-Feign");
    }

    @Test
    @DisplayName("Should verify form data contains required fields for auth code exchange")
    void shouldBuildAuthCodeFormDataCorrectly() {

        Map<String, Object> expectedResponse = createMockTokenResponse();
        when(oktaAdapter.exchangeAuthCodeForTokens(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(expectedResponse);

        service.exchangeAuthCodeForTokens(TEST_AUTH_CODE, TEST_CODE_VERIFIER, TEST_REQUEST_ID);

        verify(oktaAdapter).exchangeAuthCodeForTokens(
                anyString(),
                eq(TEST_AUTH_CODE),
                eq("https://localhost/callback"),
                eq("test-client-id"),
                eq("test-client-secret"),
                eq(TEST_CODE_VERIFIER)
        );
        verify(oktaAdapter, times(1)).resetRetryCounter();
    }

    @Test
    @DisplayName("Should verify form data contains required fields for token refresh")
    void shouldBuildRefreshTokenFormDataCorrectly() {

        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("access_token", "new-access-token");
        expectedResponse.put("expires_in", 3600);

        when(oktaAdapter.refreshAccessToken(
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(expectedResponse);

        service.refreshTokens(TEST_REFRESH_TOKEN, TEST_REQUEST_ID);

        verify(oktaAdapter).refreshAccessToken(
                anyString(),
                eq(TEST_REFRESH_TOKEN),
                eq("test-client-id"),
                eq("test-client-secret")
        );
        verify(oktaAdapter, times(1)).resetRetryCounter();
    }

    private Map<String, Object> createMockTokenResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "mock-access-token");
        response.put("id_token", "mock-id-token");
        response.put("refresh_token", "mock-refresh-token");
        response.put("token_type", "Bearer");
        response.put("expires_in", 3600);
        return response;
    }
}
