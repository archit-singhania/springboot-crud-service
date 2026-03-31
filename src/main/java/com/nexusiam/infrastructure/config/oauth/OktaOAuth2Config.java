package com.nexusiam.infrastructure.config.oauth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "okta.oauth2")
@Data
public class OktaOAuth2Config {

    private String issuer;
    private String clientId;
    private String clientSecret;
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String profileEndpoint;
    private String jwksUri;
    private String redirectUri;
    private String scope;
}
