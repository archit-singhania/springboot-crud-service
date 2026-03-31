package com.example.demo.scheduler;

import com.example.demo.dto.SSOProfileResponse;
import com.example.demo.model.SSOExchangeMaster;
import com.example.demo.repository.SSOExchangeRepository;
import com.example.demo.service.JwksService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProfileSyncScheduler {

    private final SSOExchangeRepository ssoRepo;
    private final WebClient oktaWebClient;
    private final JwksService jwksService;
    private final ObjectMapper objectMapper;

    @Value("${okta.oauth2.profile-endpoint}")
    private String profileEndpoint;

    // Run every hour at minute 0
    @Scheduled(cron = "0 0 * * * *")
    public void syncActiveProfiles() {
        log.info("Starting scheduled profile sync for active users");

        List<SSOExchangeMaster> activeRecords = ssoRepo.findAll().stream()
                .filter(record -> "ACTIVE".equals(record.getTokenStatus()))
                .filter(record -> record.getAccessToken() != null)
                .toList();

        log.info("Found {} active SSO records to sync", activeRecords.size());

        int successCount = 0;
        int failureCount = 0;

        for (SSOExchangeMaster record : activeRecords) {
            try {
                syncSingleProfile(record);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to sync profile for profileId: {}", record.getProfileId(), e);

                // Mark sync error
                record.setSyncStatus("FAILED");
                record.setSyncErrorMessage(e.getMessage());
                record.setUpdatedAt(Instant.now());
                ssoRepo.save(record);
            }
        }

        log.info("Profile sync completed. Success: {}, Failed: {}", successCount, failureCount);
    }

    private void syncSingleProfile(SSOExchangeMaster record) {
        log.debug("Syncing profile for profileId: {}", record.getProfileId());

        // Validate token is still valid
        try {
            jwksService.validateAndParseToken(record.getIdToken());
        } catch (Exception e) {
            log.warn("Token expired for profileId: {}, skipping sync", record.getProfileId());
            return;
        }

        // Fetch latest profile from Common SSO
        SSOProfileResponse profileResponse;
        try {
            profileResponse = oktaWebClient.get()
                    .uri(profileEndpoint)
                    .header("Authorization", "Bearer " + record.getAccessToken())
                    .retrieve()
                    .bodyToMono(SSOProfileResponse.class)
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch profile from SSO", e);
        }

        if (profileResponse == null) {
            throw new RuntimeException("Empty profile response");
        }

        // Update all profile fields
        updateProfileData(record, profileResponse);

        record.setSyncStatus("SUCCESS");
        record.setSyncErrorMessage(null);
        record.setUpdatedAt(Instant.now());

        ssoRepo.save(record);

        log.debug("Profile synced successfully for profileId: {}", record.getProfileId());
    }

    private void updateProfileData(SSOExchangeMaster record, SSOProfileResponse profile) {
        if (profile == null) return;

        // Update organization fields
        if (profile.getOrgId() != null) {
            record.setOrgId(profile.getOrgId());
        }

        if (profile.getProfileId() != null) {
            record.setProfileId(profile.getProfileId());
        }

        // Update company details
        record.setCompanyName(profile.getCompanyName());
        record.setCompanyAddress(profile.getCompanyAddress());
        record.setState(profile.getState());
        record.setPincode(profile.getPincode());
        record.setGstNumber(profile.getGstNumber());
        record.setCinNumber(profile.getCinNumber());
        record.setPanNumber(profile.getPanNumber());

        // Update authorized person
        record.setAuthorizedPersonName(profile.getAuthorizedPersonName());
        record.setDesignation(profile.getDesignation());
        record.setEmail(profile.getEmail());
        record.setMobile(profile.getMobile());
        record.setLandline(profile.getLandline());

        // Update status fields
        record.setStatus(profile.getStatus());
        record.setComplianceStatus(profile.getComplianceStatus());
        record.setExchangeAccess(profile.getExchangeAccess());
        record.setValidTill(profile.getValidTill());

        // Update registrations
        try {
            if (profile.getRegistrations() != null && !profile.getRegistrations().isEmpty()) {
                record.setRegistrations(objectMapper.writeValueAsString(profile.getRegistrations()));
            }

            // Store raw payload for audit
            record.setRawSsoPayload(objectMapper.writeValueAsString(profile));
        } catch (Exception e) {
            log.error("Failed to serialize profile data", e);
        }

        record.setUpdatedAt(Instant.now());
    }
}

// Enable scheduling in main application class:
// Add @EnableScheduling annotation to your main Spring Boot application class