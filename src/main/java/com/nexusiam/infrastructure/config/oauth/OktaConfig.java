package com.nexusiam.infrastructure.config.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Configuration
public class OktaConfig {

    @Value("${okta.oauth2.issuer}")
    private String issuer;

    @Bean
    public WebClient oktaWebClient() {
        String safeIssuer = Objects.requireNonNull(issuer, "okta.oauth2.issuer cannot be null");

        return WebClient.builder()
                .baseUrl(safeIssuer)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }
}
