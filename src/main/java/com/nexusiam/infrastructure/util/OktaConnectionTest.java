package com.nexusiam.infrastructure.util;

import com.nexusiam.infrastructure.config.oauth.OktaOAuth2Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;

@Component
@Profile("test-okta")
@Slf4j
@RequiredArgsConstructor
public class OktaConnectionTest implements CommandLineRunner {

    private final OktaOAuth2Config oktaConfig;

    @Override
    public void run(String... args) {
        log.info("========================================");
        log.info("Testing Okta Connectivity");
        log.info("========================================");

        testOktaConfiguration();
        testDNSResolution();
        testOktaEndpoints();

        log.info("========================================");
        log.info("Okta Connectivity Test Complete");
        log.info("========================================");
    }

    private void testOktaConfiguration() {
        log.info("Okta Configuration:");
        log.info("  Issuer: {}", oktaConfig.getIssuer());
        log.info("  Client ID: {}", maskSensitiveData(oktaConfig.getClientId()));
        log.info("  Client Secret: {}", maskSensitiveData(oktaConfig.getClientSecret()));
        log.info("  Token Endpoint: {}", oktaConfig.getTokenEndpoint());
        log.info("  Profile Endpoint: {}", oktaConfig.getProfileEndpoint());
        log.info("  Redirect URI: {}", oktaConfig.getRedirectUri());
        log.info("  Authorization Endpoint: {}", oktaConfig.getAuthorizationEndpoint());
        log.info("  JWKS URI: {}", oktaConfig.getJwksUri());
    }

    private void testDNSResolution() {
        try {
            String host = extractHost(oktaConfig.getIssuer());
            log.info("Testing DNS resolution for: {}", host);

            InetAddress address = InetAddress.getByName(host);
            log.info("✓ DNS resolved successfully: {} -> {}", host, address.getHostAddress());
        } catch (Exception e) {
            log.error("✗ DNS resolution failed: {}", e.getMessage());
        }
    }

    private void testOktaEndpoints() {
        log.info("Testing Okta endpoints...");

        testEndpoint("JWKS URI", oktaConfig.getJwksUri());

        testEndpoint("Issuer", oktaConfig.getIssuer() + "/.well-known/openid-configuration");
    }

    private void testEndpoint(String name, String endpoint) {
        try {
            log.info("Testing {}: {}", name, endpoint);

            URL url = URI.create(endpoint).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                log.info("✓ {} is reachable (Status: {})", name, responseCode);
            } else {
                log.warn("⚠ {} returned status: {}", name, responseCode);
            }

            connection.disconnect();

        } catch (java.net.UnknownHostException e) {
            log.error("✗ {} - Unknown host: {}. Check DNS or network connectivity.", name, e.getMessage());
        } catch (java.net.SocketTimeoutException e) {
            log.error("✗ {} - Connection timeout: {}. Check firewall or network.", name, e.getMessage());
        } catch (java.net.ConnectException e) {
            log.error("✗ {} - Connection refused: {}. Check if Okta URL is correct.", name, e.getMessage());
        } catch (Exception e) {
            log.error("✗ {} test failed: {}", name, e.getMessage());
        }
    }

    private String extractHost(String url) {
        try {
            URL u = URI.create(url).toURL();
            return u.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private String maskSensitiveData(String data) {
        if (data == null || data.length() < 8) {
            return "***";
        }
        return data.substring(0, 4) + "..." + data.substring(data.length() - 4);
    }
}
