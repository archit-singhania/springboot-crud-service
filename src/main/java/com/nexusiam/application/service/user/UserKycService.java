package com.nexusiam.application.service.user;

import com.nexusiam.application.dto.response.OrganizationProfileResponse;
import com.nexusiam.application.dto.request.UserKycRequest;
import com.nexusiam.application.dto.response.UserKycResponse;
import com.nexusiam.application.dto.response.VirtualAccountResponse;

public interface UserKycService {

    UserKycResponse createKyc(UserKycRequest request);

    UserKycResponse updateKyc(Long id, UserKycRequest request);

    UserKycResponse getKycByGrpId(String grpId);

    UserKycResponse getKycById(Long id);

    Boolean isQuarterLockActive(String grpId);

    Boolean canUpdateKyc(String grpId);

    String maskAccountNumber(String accountNumber);

    OrganizationProfileResponse getOrganizationProfile(String grpId, String token);

    VirtualAccountResponse getVirtualAccountDetails(String grpId);
}
