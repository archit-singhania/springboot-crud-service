package com.nexusiam.infrastructure.config.oauth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "exchange.token")
@Data
public class ExchangeTokenConfig {

    private String issuer = "https://iam-nexus.com";
    private int expiryMinutes = 15;
    private int refreshExpiryHours = 24;
    private String audience = "IAM_NEXUS";
}
