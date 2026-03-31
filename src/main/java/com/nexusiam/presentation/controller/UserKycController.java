package com.nexusiam.presentation.controller;

import com.nexusiam.application.dto.response.ApiResponse;
import com.nexusiam.application.dto.response.OrganizationProfileResponse;
import com.nexusiam.application.dto.request.UserKycRequest;
import com.nexusiam.application.dto.response.UserKycResponse;
import com.nexusiam.application.dto.response.VirtualAccountResponse;
import com.nexusiam.presentation.exception.SSOException;
import com.nexusiam.infrastructure.security.context.AuthenticatedUserContext;
import com.nexusiam.application.service.user.UserKycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exchange/v1/sso/kyc")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User KYC & Bank Setup", description = "Production-grade APIs for managing user KYC and bank account details with full authentication")
@SecurityRequirement(name = "Bearer Authentication")
public class UserKycController {

    private final UserKycService userKycService;
    private final AuthenticatedUserContext userContext;

    @GetMapping("/organization-profile")
    @Operation(
        summary = "Get organization profile",
        description = "Retrieve organization profile with units for the authenticated user"
    )
    public ResponseEntity<ApiResponse<OrganizationProfileResponse>> getOrganizationProfile(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String authenticatedGrpId = extractAuthenticatedGrpId();

        try {
            if (authenticatedGrpId == null || authenticatedGrpId.trim().isEmpty()) {
                throw new SSOException("Unable to identify user group. Please re-authenticate.");
            }

            String token = null;
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                token = authorizationHeader.substring(7);
            }

            OrganizationProfileResponse response = userKycService.getOrganizationProfile(authenticatedGrpId, token);

            return ResponseEntity.ok(ApiResponse.<OrganizationProfileResponse>builder()
                    .success(true)
                    .message("Organization profile retrieved successfully")
                    .data(response)
                    .build());
        } catch (SSOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<OrganizationProfileResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error fetching organization profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<OrganizationProfileResponse>builder()
                            .success(false)
                            .message("An unexpected error occurred.")
                            .build());
        }
    }

    @GetMapping("/virtual-account")
    @Operation(
        summary = "Get virtual account details",
        description = "Retrieve virtual account details for depositing funds"
    )
    public ResponseEntity<ApiResponse<VirtualAccountResponse>> getVirtualAccount() {
        String authenticatedGrpId = extractAuthenticatedGrpId();

        try {
            if (authenticatedGrpId == null || authenticatedGrpId.trim().isEmpty()) {
                throw new SSOException("Unable to identify user group. Please re-authenticate.");
            }

            VirtualAccountResponse response = userKycService.getVirtualAccountDetails(authenticatedGrpId);

            if (response == null) {
                return ResponseEntity.ok(ApiResponse.<VirtualAccountResponse>builder()
                        .success(false)
                        .message("No virtual account found. Please complete your bank account setup first.")
                        .data(null)
                        .build());
            }

            return ResponseEntity.ok(ApiResponse.<VirtualAccountResponse>builder()
                    .success(true)
                    .message("Virtual account details retrieved successfully")
                    .data(response)
                    .build());
        } catch (SSOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<VirtualAccountResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error fetching virtual account: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<VirtualAccountResponse>builder()
                            .success(false)
                            .message("An unexpected error occurred.")
                            .build());
        }
    }

    @PostMapping
    @Operation(
        summary = "Create KYC record",
        description = "Create a new KYC and bank account record. User must be authenticated. GrpId is automatically extracted from JWT token."
    )
    public ResponseEntity<ApiResponse<UserKycResponse>> createKyc(@Valid @RequestBody UserKycRequest request) {
        String authenticatedGrpId = extractAuthenticatedGrpId();

        try {

            request.setGrpId(authenticatedGrpId);

            if (authenticatedGrpId == null || authenticatedGrpId.trim().isEmpty()) {
                throw new SSOException("Unable to identify user group. Please re-authenticate.");
            }

            UserKycResponse response = userKycService.createKyc(request);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(true)
                            .message("KYC record created and verified successfully")
                            .data(response)
                            .build());
        } catch (SSOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error creating KYC: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message("An unexpected error occurred. Please try again or contact support.")
                            .build());
        }
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update KYC record",
        description = "Update an existing KYC record. User can only update their own KYC records."
    )
    public ResponseEntity<ApiResponse<UserKycResponse>> updateKyc(
            @PathVariable Long id,
            @Valid @RequestBody UserKycRequest request) {

        String authenticatedGrpId = extractAuthenticatedGrpId();

        try {

            request.setGrpId(authenticatedGrpId);

            if (authenticatedGrpId == null || authenticatedGrpId.trim().isEmpty()) {
                throw new SSOException("Unable to identify user group. Please re-authenticate.");
            }

            UserKycResponse existingKyc = userKycService.getKycById(id);
            if (!existingKyc.getGrpId().equals(authenticatedGrpId)) {
                log.warn("Authorization failed - User {} attempted to update KYC belonging to {}",
                         authenticatedGrpId, existingKyc.getGrpId());
                throw new SSOException("You are not authorized to update this KYC record.");
            }

            UserKycResponse response = userKycService.updateKyc(id, request);

            return ResponseEntity.ok(ApiResponse.<UserKycResponse>builder()
                    .success(true)
                    .message("KYC record updated and verified successfully")
                    .data(response)
                    .build());
        } catch (SSOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error updating KYC: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message("An unexpected error occurred. Please try again or contact support.")
                            .build());
        }
    }

    @GetMapping("/my-kyc")
    @Operation(
        summary = "Get my KYC details",
        description = "Retrieve KYC details for the authenticated user. Automatically uses grpId from JWT token."
    )
    public ResponseEntity<ApiResponse<UserKycResponse>> getMyKyc() {
        String authenticatedGrpId = extractAuthenticatedGrpId();

        try {
            if (authenticatedGrpId == null || authenticatedGrpId.trim().isEmpty()) {
                throw new SSOException("Unable to identify user group. Please re-authenticate.");
            }

            UserKycResponse response = userKycService.getKycByGrpId(authenticatedGrpId);

            if (response == null) {
                return ResponseEntity.ok(ApiResponse.<UserKycResponse>builder()
                        .success(false)
                        .message("No KYC record found. Please complete your KYC verification.")
                        .data(null)
                        .build());
            }

            return ResponseEntity.ok(ApiResponse.<UserKycResponse>builder()
                    .success(true)
                    .message("KYC record retrieved successfully")
                    .data(response)
                    .build());
        } catch (SSOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error fetching KYC: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message("An unexpected error occurred. Please try again or contact support.")
                            .build());
        }
    }

    @GetMapping("/group/{grpId}")
    @Operation(
        summary = "Get KYC by group ID (Admin)",
        description = "Retrieve KYC details for a specific group. Requires admin permissions."
    )
    public ResponseEntity<ApiResponse<UserKycResponse>> getKycByGrpId(@PathVariable String grpId) {
        try {
            UserKycResponse response = userKycService.getKycByGrpId(grpId);

            return ResponseEntity.ok(ApiResponse.<UserKycResponse>builder()
                    .success(true)
                    .message("KYC record retrieved successfully")
                    .data(response)
                    .build());
        } catch (SSOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error fetching KYC by grpId: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message("An unexpected error occurred.")
                            .build());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get KYC by ID (Admin)", description = "Retrieve KYC details by record ID")
    public ResponseEntity<ApiResponse<UserKycResponse>> getKycById(@PathVariable Long id) {
        try {
            UserKycResponse response = userKycService.getKycById(id);

            return ResponseEntity.ok(ApiResponse.<UserKycResponse>builder()
                    .success(true)
                    .message("KYC record retrieved successfully")
                    .data(response)
                    .build());
        } catch (SSOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error fetching KYC by ID: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<UserKycResponse>builder()
                            .success(false)
                            .message("An unexpected error occurred.")
                            .build());
        }
    }

    @GetMapping("/can-update")
    @Operation(
        summary = "Check if KYC can be updated",
        description = "Check if authenticated user can update their KYC (quarter lock check)"
    )
    public ResponseEntity<ApiResponse<Boolean>> canUpdateMyKyc() {
        String authenticatedGrpId = extractAuthenticatedGrpId();

        try {
            if (authenticatedGrpId == null || authenticatedGrpId.trim().isEmpty()) {
                throw new SSOException("Unable to identify user group. Please re-authenticate.");
            }

            Boolean canUpdate = userKycService.canUpdateKyc(authenticatedGrpId);
            String message = canUpdate ? "KYC can be updated" : "KYC update is locked due to quarter lock period";

            return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                    .success(true)
                    .message(message)
                    .data(canUpdate)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<Boolean>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/quarter-lock")
    @Operation(
        summary = "Check quarter lock status",
        description = "Check if quarter lock is active for authenticated user"
    )
    public ResponseEntity<ApiResponse<Boolean>> isMyQuarterLockActive() {
        String authenticatedGrpId = extractAuthenticatedGrpId();

        try {
            if (authenticatedGrpId == null || authenticatedGrpId.trim().isEmpty()) {
                throw new SSOException("Unable to identify user group. Please re-authenticate.");
            }

            Boolean isLocked = userKycService.isQuarterLockActive(authenticatedGrpId);
            String message = isLocked ? "Quarter lock is active" : "No quarter lock active";

            return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                    .success(true)
                    .message(message)
                    .data(isLocked)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<Boolean>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    private String extractAuthenticatedGrpId() {
        try {
            return userContext.getAuthenticatedGrpId();
        } catch (Exception e) {
            log.error("Exception while extracting grpId: {}", e.getMessage(), e);
            return null;
        }
    }
}
