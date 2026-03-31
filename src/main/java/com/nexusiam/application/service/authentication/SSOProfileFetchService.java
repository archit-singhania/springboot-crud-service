package com.nexusiam.application.service.authentication;

import com.nexusiam.application.service.enricher.ProfileDataEnricherHardcode;
import com.nexusiam.infrastructure.config.oauth.OktaOAuth2Config;
import com.nexusiam.application.dto.response.SSOProfileResponse;
import com.nexusiam.presentation.exception.SSOProfileFetchException;
import com.nexusiam.presentation.exception.SSOProfileValidationException;
import com.nexusiam.presentation.exception.SSOConfigurationException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class SSOProfileFetchService {

    private final WebClient oktaWebClient;
    private final ProfileDataEnricherHardcode profileDataEnricher;
    private final OktaOAuth2Config oktaConfig;

    public SSOProfileResponse fetchProfile(@NonNull String accessToken, @NonNull String requestId) {
        log.info("[RequestID: {}] Fetching profile from: {}", requestId, oktaConfig.getProfileEndpoint());

        final String endpoint;
        try {
            endpoint = Objects.requireNonNull(oktaConfig.getProfileEndpoint(),
                "Profile endpoint configuration is missing");
        } catch (NullPointerException e) {
            throw new SSOConfigurationException("Profile endpoint configuration is missing", e);
        }

        try {
            SSOProfileResponse profileResponse = oktaWebClient.get()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Request-ID", requestId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("[RequestID: {}] SSO profile error: {}", requestId, body);
                                        SSOProfileFetchException ex = new SSOProfileFetchException("SSO profile error: " + body);
                                        ex.setRequestId(requestId);
                                        return reactor.core.publisher.Mono.error(ex);
                                    })
                    )
                    .bodyToMono(SSOProfileResponse.class)
                    .block();

            validateProfileResponse(profileResponse, requestId);

            enrichProfileIfNeeded(profileResponse, requestId);

            return profileResponse;

        } catch (SSOProfileFetchException | SSOProfileValidationException | SSOConfigurationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RequestID: {}] Failed to fetch profile from SSO", requestId, e);
            throw new SSOProfileFetchException("Failed to fetch profile from SSO", e);
        }
    }

    private void validateProfileResponse(SSOProfileResponse profile, String requestId) {
        if (profile == null) {
            log.error("[RequestID: {}] Empty profile response from SSO", requestId);
            throw new SSOProfileFetchException("Failed to retrieve user profile");
        }

        if (profile.getProfileId() == null || profile.getProfileId().isEmpty()) {
            throw new SSOProfileValidationException("Invalid profile response: missing profile_id");
        }
    }

    private void enrichProfileIfNeeded(SSOProfileResponse profile, String requestId) {
        if (profile.getCompanyName() == null || profile.getCompanyName().isEmpty()) {
            log.warn("[RequestID: {}] SSO response missing company data, generating test data", requestId);
            profileDataEnricher.enrichWithTestData(profile);
        }

        if (profile.getRegistrations() == null || profile.getRegistrations().isEmpty()) {
            log.warn("[RequestID: {}] SSO response missing registrations, generating test registrations for email: {}",
                requestId, profile.getEmail());
            profile.setRegistrations(profileDataEnricher.generateTestRegistrations(profile.getGrpId(), profile.getEmail()));
        }
    }
}
