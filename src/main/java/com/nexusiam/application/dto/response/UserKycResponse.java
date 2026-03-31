package com.nexusiam.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserKycResponse {

    private Long id;
    private String grpId;
    private Short userTypeId;
    private String actHolderName;
    private String actNumberMasked;
    private String bankIfsc;
    private String bankName;
    private String bankBranch;
    private Short status;
    private String statusLabel;
    private ZonedDateTime verifiedAt;
    private ZonedDateTime lastUpdatedAt;
    private ZonedDateTime quarterLockDate;
    private Boolean isActive;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private Boolean canUpdate;
    private String quarterLockReason;
}
