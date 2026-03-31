package com.nexusiam.application.service.orchestrator;

import com.nexusiam.application.dto.response.SSOProfileResponse;
import com.nexusiam.application.service.factory.SSOUserGroupFactory;
import com.nexusiam.application.service.mapper.UserGroupDataMapper;
import com.nexusiam.application.service.user.UserRoleMappingService;
import com.nexusiam.core.domain.entity.SSOUserGroup;
import com.nexusiam.core.domain.entity.UserType;
import com.nexusiam.core.domain.repository.SSOUserGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserGroupOrchestrator {

    private final SSOUserGroupRepository userGroupRepo;
    private final SSOUserGroupFactory userGroupFactory;
    private final UserGroupDataMapper userGroupDataMapper;
    private final UserRoleMappingService userRoleMappingService;

    @Transactional
    public SSOUserGroup getOrCreateAndUpdateUserGroup(String profileId, String grpId,
                                                      SSOProfileResponse profile) {
        log.debug("Processing user group for profileId: {}, grpId: {} using UPSERT", profileId, grpId);

        SSOUserGroup userGroup = userGroupRepo.findByProfileId(profileId)
                .orElseGet(() -> {
                    log.info("Preparing new user group for profileId: {}", profileId);
                    return userGroupFactory.createFromProfile(profileId, grpId, profile);
                });

        userGroupDataMapper.updateUserGroupFromProfile(userGroup, profile);

        UserType userType = userRoleMappingService.getUserTypeForUser(
            profile.getEmail(),
            profile.getRegistrations()
        );
        userGroup.setUserTypeId(userType.getId());

        userGroupRepo.upsertUserGroup(userGroup);

        userGroup = userGroupRepo.findByProfileId(profileId)
                .orElseThrow(() -> new RuntimeException("Failed to retrieve user group after upsert"));

        log.info("UPSERT completed: Assigned role '{}' with type '{}' to user {}",
            userType.getRole(), userType.getType(), profile.getEmail());

        return userGroup;
    }
}
