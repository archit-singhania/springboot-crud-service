package com.nexusiam.application.service.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusiam.application.dto.response.OrganizationProfileResponse;
import com.nexusiam.application.dto.request.UserKycRequest;
import com.nexusiam.application.dto.response.UserKycResponse;
import com.nexusiam.application.dto.response.VirtualAccountResponse;
import com.nexusiam.presentation.exception.SSOException;
import com.nexusiam.core.domain.entity.SSOUserGroup;
import com.nexusiam.core.domain.entity.UserKyc;
import com.nexusiam.core.domain.repository.SSOUserGroupRepository;
import com.nexusiam.core.domain.repository.UserKycRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserKycManagementService implements UserKycService {

    private final UserKycRepository userKycRepository;
    private final SSOUserGroupRepository userGroupRepository;
    private final ObjectMapper objectMapper;
    private static final int QUARTER_LOCK_DAYS = 90;

    @Override
    @Transactional
    public UserKycResponse createKyc(UserKycRequest request) {

        if (!request.getActNumber().equals(request.getActNumberConfirm())) {
            throw new SSOException("Account number and confirmation do not match");
        }

        if (request.getConsent() == null || !request.getConsent()) {
            throw new SSOException("Consent is required for KYC verification");
        }

        if (userKycRepository.existsByGrpId(request.getGrpId())) {
            throw new SSOException("KYC record already exists for this group. Please use update instead.");
        }

        ZonedDateTime now = ZonedDateTime.now();
        UserKyc kyc = UserKyc.builder()
                .grpId(request.getGrpId())
                .userTypeId(request.getUserTypeId())
                .actHolderName(request.getActHolderName())
                .actNumber(request.getActNumber())
                .actNumberMasked(maskAccountNumber(request.getActNumber()))
                .bankIfsc(request.getBankIfsc().toUpperCase())
                .bankName(request.getBankName())
                .bankBranch(request.getBankBranch())
                .status((short) 1)
                .verifiedAt(now)
                .quarterLockDate(now)
                .isActive(true)
                .build();

        UserKyc savedKyc = userKycRepository.save(kyc);

        return mapToResponse(savedKyc);
    }

    @Override
    @Transactional
    public UserKycResponse updateKyc(Long id, UserKycRequest request) {

        UserKyc existingKyc = userKycRepository.findById(id)
                .orElseThrow(() -> new SSOException("KYC record not found with ID: " + id));

        if (!request.getActNumber().equals(request.getActNumberConfirm())) {
            throw new SSOException("Account number and confirmation do not match");
        }

        existingKyc.setIsActive(false);
        userKycRepository.save(existingKyc);

        ZonedDateTime now = ZonedDateTime.now();
        UserKyc newKyc = UserKyc.builder()
                .grpId(request.getGrpId())
                .userTypeId(request.getUserTypeId())
                .actHolderName(request.getActHolderName())
                .actNumber(request.getActNumber())
                .actNumberMasked(maskAccountNumber(request.getActNumber()))
                .bankIfsc(request.getBankIfsc().toUpperCase())
                .bankName(request.getBankName())
                .bankBranch(request.getBankBranch())
                .status((short) 1)
                .verifiedAt(now)
                .quarterLockDate(now)
                .isActive(true)
                .lastUpdatedAt(now)
                .build();

        UserKyc savedKyc = userKycRepository.save(newKyc);

        return mapToResponse(savedKyc);
    }

    @Override
    public UserKycResponse getKycByGrpId(String grpId) {

        UserKyc kyc = userKycRepository.findByGrpIdAndIsActiveTrue(grpId)
                .orElse(null);

        if (kyc == null) {
            log.info("No KYC record found for group: {} - This is normal for first-time users", grpId);
            return null;
        }

        return mapToResponse(kyc);
    }

    @Override
    public UserKycResponse getKycById(Long id) {

        UserKyc kyc = userKycRepository.findById(id)
                .orElseThrow(() -> new SSOException("KYC record not found with ID: " + id));

        return mapToResponse(kyc);
    }

    @Override
    public Boolean isQuarterLockActive(String grpId) {
        return false;

    }

    @Override
    public Boolean canUpdateKyc(String grpId) {
         return true;

    }

    @Override
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        int visibleDigits = 4;
        String lastDigits = accountNumber.substring(accountNumber.length() - visibleDigits);
        return "X".repeat(accountNumber.length() - visibleDigits) + lastDigits;
    }

    @Override
    public OrganizationProfileResponse getOrganizationProfile(String grpId, String token) {
        SSOUserGroup userGroup = userGroupRepository.findByGrpId(grpId)
                .orElseThrow(() -> new SSOException("Organization not found for group: " + grpId));

        List<String> tokenAvailableRoles = new ArrayList<>();
        JsonNode tokenRegistrations = null;

        if (token != null && !token.trim().isEmpty()) {
            try {

                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    JsonNode claims = objectMapper.readTree(payload);

                    if (claims.has("available_roles") && claims.get("available_roles").isArray()) {
                        for (JsonNode role : claims.get("available_roles")) {
                            tokenAvailableRoles.add(role.asText());
                        }
                    }

                    if (claims.has("registrations")) {
                        tokenRegistrations = claims.get("registrations");
                    }

                    log.info("✅ Extracted from JWT token - available_roles: {}", tokenAvailableRoles);
                }
            } catch (Exception e) {
                log.error("❌ Failed to parse JWT token: {}", e.getMessage());
            }
        }

        JsonNode registrationsToUse = tokenRegistrations != null ? tokenRegistrations : userGroup.getRegistrations();
        List<String> availableRolesToUse = !tokenAvailableRoles.isEmpty() ? tokenAvailableRoles : extractRolesFromUserGroup(userGroup);

        List<OrganizationProfileResponse.UnitInfo> units = extractUnitsFromRegistrations(
            userGroup,
            registrationsToUse,
            availableRolesToUse
        );

        return OrganizationProfileResponse.builder()
                .name(userGroup.getCompanyName())
                .cin(userGroup.getCinNumber())
                .pan(userGroup.getGroupPan())
                .grpId(userGroup.getGrpId())
                .roles(availableRolesToUse)
                .authorizedPerson(userGroup.getAuthorizedPersonName())
                .units(units)
                .build();
    }

    @Override
    public VirtualAccountResponse getVirtualAccountDetails(String grpId) {

        UserKyc kyc = userKycRepository.findByGrpIdAndIsActiveTrue(grpId)
                .orElse(null);

        if (kyc == null) {
            log.info("No virtual account available for group: {} - KYC not yet completed", grpId);
            return null;
        }

        if (kyc.getStatus() != 1) {
            log.info("Virtual account not available for group: {} - KYC verification pending", grpId);
            return null;
        }

        String accountNumber = generateVirtualAccountFromBankAccount(kyc.getActNumber());

        return VirtualAccountResponse.builder()
                .accountNumber(accountNumber)
                .bankIfsc(kyc.getBankIfsc())
                .bankName(kyc.getBankName() != null ? kyc.getBankName() : "Bank details pending")
                .bankBranch(kyc.getBankBranch() != null ? kyc.getBankBranch() : "Branch details pending")
                .upiHandle(accountNumber + "@" + kyc.getBankIfsc())
                .build();
    }

    private List<String> extractRolesFromUserGroup(SSOUserGroup userGroup) {
        List<String> roles = new ArrayList<>();

        if (userGroup.getRegistrations() == null) {
            return roles;
        }

        try {
            if (userGroup.getRegistrations().isArray()) {
                for (JsonNode registration : userGroup.getRegistrations()) {
                    String role = registration.has("role") ? registration.get("role").asText() : null;
                    if (role != null) {

                        String exchangeRole = mapEPRRoleToExchangeRole(role);
                        if (exchangeRole != null && !roles.contains(exchangeRole.toUpperCase())) {
                            roles.add(exchangeRole.toUpperCase());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting roles from registrations: {}", e.getMessage());
        }

        return roles;
    }

    private String mapEPRRoleToExchangeRole(String eprRole) {
        if (eprRole == null || eprRole.isEmpty()) {
            return null;
        }

        String roleLower = eprRole.toLowerCase();

        if (roleLower.contains("brand") ||
            roleLower.contains("producer") ||
            roleLower.contains("pibo") ||
            roleLower.contains("manufacturer")) {
            return "Buyer";
        }

        if (roleLower.contains("recycl") ||
            roleLower.contains("aggregat") ||
            roleLower.contains("collect")) {
            return "Seller";
        }

        return "Buyer";
    }

    private List<OrganizationProfileResponse.UnitInfo> extractUnitsFromRegistrations(
            SSOUserGroup userGroup,
            JsonNode registrations,
            List<String> availableRoles) {
        List<OrganizationProfileResponse.UnitInfo> units = new ArrayList<>();

        if (registrations == null) {
            return units;
        }

        try {

            JsonNode portalsArray = null;
            if (registrations.has("portals") && registrations.get("portals").isArray()) {
                portalsArray = registrations.get("portals");
            } else if (registrations.isArray()) {
                portalsArray = registrations;
            }

            if (portalsArray != null) {
                int index = 1;
                for (JsonNode portal : portalsArray) {
                    String unitId = portal.has("unit_id") ? portal.get("unit_id").asText() : "U-" + String.format("%03d", index);
                    String portalNameFull = portal.has("portal_name") ? portal.get("portal_name").asText() : "Unknown Portal";

                    String portalName = portalNameFull.split("\\s+")[0];

                    String role = portal.has("role") ? portal.get("role").asText() : "";
                    String unitType = determineUnitType(portal);
                    String gstin = portal.has("gst_number") ? portal.get("gst_number").asText() : "";
                    String registrationType = role;
                    String address = portal.has("unit_address") ? portal.get("unit_address").asText() : "";
                    String state = portal.has("state") ? portal.get("state").asText() : "";
                    String pincode = portal.has("pincode") ? portal.get("pincode").asText() : "";

                    if (!address.isEmpty() && !state.isEmpty()) {
                        address = address + ", " + state;
                        if (!pincode.isEmpty()) {
                            address = address + " – " + pincode;
                        }
                    }

                    List<String> unitRoles = new ArrayList<>();
                    for (String availableRole : availableRoles) {

                        String capitalizedRole = availableRole.substring(0, 1).toUpperCase() +
                                               availableRole.substring(1).toLowerCase();

                        String formattedRole = capitalizedRole + " - " + portalName;
                        unitRoles.add(formattedRole);
                    }

                    OrganizationProfileResponse.UnitInfo unit = OrganizationProfileResponse.UnitInfo.builder()
                            .id(unitId)
                            .name("Unit " + index + " – " + portalNameFull + " (" + state + ")")
                            .type(unitType)
                            .gstin(gstin)
                            .registrationType(registrationType)
                            .address(address)
                            .roles(unitRoles)
                            .authorizedPerson(userGroup.getAuthorizedPersonName() + ", " + userGroup.getDesignation())
                            .phone(userGroup.getMobile())
                            .email(userGroup.getEmail())
                            .build();

                    units.add(unit);
                    index++;
                }
            }
        } catch (Exception e) {
            log.error("Error extracting units from registrations: {}", e.getMessage());
        }

        return units;
    }

    private String determineUnitType(JsonNode registration) {
        if (!registration.has("role")) {
            return "Manufacturing";
        }

        String role = registration.get("role").asText().toLowerCase();

        if (role.contains("recycl")) {
            return "Recycling";
        } else if (role.contains("producer") || role.contains("pibo") || role.contains("brand")) {
            return "Manufacturing";
        } else if (role.contains("aggregat") || role.contains("collect")) {
            return "Collection / Aggregation";
        } else {
            return "Manufacturing";
        }
    }

    private String generateVirtualAccountFromBankAccount(String actualAccountNumber) {

        return actualAccountNumber;
    }

    private UserKycResponse mapToResponse(UserKyc kyc) {
        String statusLabel = kyc.getStatus() == 1 ? "Verified" : "Pending";
        Boolean canUpdate = canUpdateKyc(kyc.getGrpId());
        String quarterLockReason = null;

        if (isQuarterLockActive(kyc.getGrpId()) && kyc.getQuarterLockDate() != null) {
            ZonedDateTime lockDate = kyc.getQuarterLockDate();
            ZonedDateTime unlockDate = lockDate.plusDays(QUARTER_LOCK_DAYS);
            long remainingDays = ChronoUnit.DAYS.between(ZonedDateTime.now(), unlockDate);
            quarterLockReason = String.format(
                    "A bank account change was recorded on %s. Another change can be initiated on or after %s (in %d days).",
                    lockDate.toLocalDate(),
                    unlockDate.toLocalDate(),
                    remainingDays
            );
        }

        return UserKycResponse.builder()
                .id(kyc.getId())
                .grpId(kyc.getGrpId())
                .userTypeId(kyc.getUserTypeId())
                .actHolderName(kyc.getActHolderName())
                .actNumberMasked(kyc.getActNumberMasked())
                .bankIfsc(kyc.getBankIfsc())
                .bankName(kyc.getBankName())
                .bankBranch(kyc.getBankBranch())
                .status(kyc.getStatus())
                .statusLabel(statusLabel)
                .verifiedAt(kyc.getVerifiedAt())
                .lastUpdatedAt(kyc.getLastUpdatedAt())
                .quarterLockDate(kyc.getQuarterLockDate())
                .isActive(kyc.getIsActive())
                .createdAt(kyc.getCreatedAt())
                .updatedAt(kyc.getUpdatedAt())
                .canUpdate(canUpdate)
                .quarterLockReason(quarterLockReason)
                .build();
    }
}
