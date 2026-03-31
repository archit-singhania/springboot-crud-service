package com.nexusiam.application.service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusiam.application.service.user.RegistrationService;
import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.shared.constants.SSOErrorCode;
import com.nexusiam.application.dto.RegistrationDTO;
import com.nexusiam.presentation.exception.SSOProfileValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationValidationService {

    private final RegistrationService registrationService;

    public void validateRegistrationCount(JsonNode registrationsJson) {
        if (registrationsJson == null || !registrationsJson.isArray()) {
            return;
        }

        int count = registrationsJson.size();
        if (count > SSOConstants.MAX_REGISTRATIONS_PER_USER) {
            log.error("Registration count {} exceeds maximum limit of {}",
                count, SSOConstants.MAX_REGISTRATIONS_PER_USER);
            throw new SSOProfileValidationException(SSOErrorCode.INVALID_PROFILE_DATA);
        }

        log.debug("Registration count validation passed: {} registrations", count);
    }

    public void validateAuthorizedCategoriesCount(List<String> categories) {
        if (categories == null) {
            return;
        }

        if (categories.size() > SSOConstants.MAX_AUTHORIZED_CATEGORIES) {
            log.error("Authorized categories count {} exceeds maximum limit of {}",
                categories.size(), SSOConstants.MAX_AUTHORIZED_CATEGORIES);
            throw new SSOProfileValidationException(SSOErrorCode.INVALID_PROFILE_DATA);
        }
    }

    public void validateAllRegistrations(JsonNode registrationsJson) {
        validateRegistrationCount(registrationsJson);

        List<RegistrationDTO> registrations = registrationService.parseRegistrations(registrationsJson);

        for (RegistrationDTO registration : registrations) {
            validateSingleRegistration(registration);
        }

        log.info("All {} registrations validated successfully", registrations.size());
    }

    public void validateSingleRegistration(RegistrationDTO registration) {
        if (registration == null) {
            throw new SSOProfileValidationException(SSOErrorCode.INVALID_REGISTRATION);
        }

        if (isBlank(registration.getPortalId())) {
            throw new SSOProfileValidationException(SSOErrorCode.INVALID_REGISTRATION);
        }

        if (isBlank(registration.getStatus())) {
            throw new SSOProfileValidationException(SSOErrorCode.INVALID_REGISTRATION);
        }

        validateAuthorizedCategoriesCount(registration.getAuthorizedCategories());

        log.debug("Registration validation passed for portalId: {}, unitId: {}",
            registration.getPortalId(), registration.getUnitId());
    }

    public boolean isRegistrationActiveAndValid(RegistrationDTO registration) {
        if (registration == null) {
            return false;
        }

        boolean isActive = SSOConstants.STATUS_ACTIVE.equalsIgnoreCase(registration.getStatus());

        boolean isValid = registration.getValidTill() != null &&
                         registration.getValidTill().isAfter(Instant.now());

        return isActive && isValid;
    }

    public List<RegistrationDTO> getActiveAndValidRegistrations(JsonNode registrationsJson) {
        List<RegistrationDTO> allRegistrations = registrationService.parseRegistrations(registrationsJson);

        return allRegistrations.stream()
                .filter(this::isRegistrationActiveAndValid)
                .toList();
    }

    public boolean hasValidRegistrationForCategory(JsonNode registrationsJson, String categoryType) {
        List<RegistrationDTO> validRegistrations = getActiveAndValidRegistrations(registrationsJson);

        return validRegistrations.stream()
                .anyMatch(reg -> matchesCategoryType(reg, categoryType));
    }

    private boolean matchesCategoryType(RegistrationDTO registration, String categoryType) {
        if (categoryType == null || registration == null) {
            return false;
        }

        if (categoryType.equalsIgnoreCase(registration.getTypeId())) {
            return true;
        }

        if (registration.getAuthorizedCategories() != null) {
            return registration.getAuthorizedCategories().stream()
                    .anyMatch(cat -> categoryType.equalsIgnoreCase(cat) || "ALL".equalsIgnoreCase(cat));
        }

        return false;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
