package com.nexusiam.application.service.factory;

import com.nexusiam.application.dto.response.SSOProfileResponse;
import com.nexusiam.application.service.user.RegistrationService;
import com.nexusiam.core.domain.entity.SSOUserGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SSOUserGroupFactory {

    private final RegistrationService registrationService;

    public SSOUserGroup createFromProfile(String profileId, String grpId, SSOProfileResponse profile) {
        log.debug("Creating user group for profileId: {}, grpId: {}", profileId, grpId);

        return SSOUserGroup.builder()
                .profileId(profileId)
                .grpId(grpId)
                .email(profile != null ? profile.getEmail() : null)
                .userTypeId((short) 1)
                .registrations(registrationService.createEmptyRegistrations())
                .build();
    }

    public SSOUserGroup createEmpty(String profileId, String grpId) {
        log.debug("Creating empty user group for profileId: {}, grpId: {}", profileId, grpId);

        return SSOUserGroup.builder()
                .profileId(profileId)
                .grpId(grpId)
                .userTypeId((short) 1)
                .registrations(registrationService.createEmptyRegistrations())
                .build();
    }
}
