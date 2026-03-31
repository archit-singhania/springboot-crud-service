package com.nexusiam.application.dto.request;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserKycRequest {

    @NotBlank(message = "Group ID is required")
    private String grpId;

    private Short userTypeId;

    @NotBlank(message = "Account holder name is required")
    @Size(max = 255, message = "Account holder name must not exceed 255 characters")
    private String actHolderName;

    @NotBlank(message = "Account number is required")
    @Size(min = 9, max = 18, message = "Account number must be between 9 and 18 digits")

    private String actNumber;

    @NotBlank(message = "Confirm account number is required")
    private String actNumberConfirm;

    @NotBlank(message = "IFSC code is required")

    @Size(min = 11, max = 11, message = "IFSC code must be exactly 11 characters")
    private String bankIfsc;

    private String bankName;

    private String bankBranch;

    private Boolean consent;
}
