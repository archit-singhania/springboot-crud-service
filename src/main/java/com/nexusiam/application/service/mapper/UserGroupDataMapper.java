package com.nexusiam.application.service.mapper;

import com.nexusiam.application.service.user.RegistrationService;
import com.nexusiam.shared.constants.SSOConstants;
import com.nexusiam.application.dto.RegistrationDTO;
import com.nexusiam.application.dto.response.SSOProfileResponse;
import com.nexusiam.core.domain.entity.SSOUserGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserGroupDataMapper {

    private final RegistrationService registrationService;

    public void updateUserGroupFromProfile(SSOUserGroup userGroup, SSOProfileResponse profile) {
        if (profile == null) {
            ensureRequiredFields(userGroup, "unknown", "unknown@exchange.local");
            return;
        }

        userGroup.setCompanyName(
            profile.getCompanyName() != null && !profile.getCompanyName().isEmpty()
                ? profile.getCompanyName()
                : generateCompanyName(profile.getGrpId(), profile.getEmail())
        );

        userGroup.setCinNumber(
            profile.getCinNumber() != null && !profile.getCinNumber().isEmpty()
                ? profile.getCinNumber()
                : generateCIN(profile.getGrpId())
        );

        userGroup.setGroupPan(
            profile.getPanNumber() != null && !profile.getPanNumber().isEmpty()
                ? profile.getPanNumber()
                : generatePAN(profile.getGrpId(), userGroup.getCompanyName())
        );

        userGroup.setAuthorizedPersonName(
            profile.getAuthorizedPersonName() != null && !profile.getAuthorizedPersonName().isEmpty()
                ? profile.getAuthorizedPersonName()
                : profile.getName() != null && !profile.getName().isEmpty()
                    ? profile.getName()
                    : extractNameFromEmail(profile.getEmail())
        );

        userGroup.setDesignation(
            profile.getDesignation() != null && !profile.getDesignation().isEmpty()
                ? profile.getDesignation()
                : SSOConstants.DEFAULT_DESIGNATION
        );

        String email = profile.getEmail();
        userGroup.setEmail(
            email != null && !email.isEmpty() && email.contains("@")
                ? email
                : profile.getProfileId() + "@exchange.local"
        );

        userGroup.setMobile(
            profile.getMobile() != null && !profile.getMobile().isEmpty()
                ? profile.getMobile()
                : generateMobile(profile.getGrpId())
        );

        userGroup.setLandline(profile.getLandline());

        userGroup.setStatus(
            profile.getStatus() != null && !profile.getStatus().isEmpty()
                ? profile.getStatus()
                : SSOConstants.DEFAULT_STATUS
        );

        userGroup.setComplianceStatus(
            profile.getComplianceStatus() != null && !profile.getComplianceStatus().isEmpty()
                ? profile.getComplianceStatus()
                : SSOConstants.DEFAULT_COMPLIANCE_STATUS
        );

        userGroup.setExchangeAccess(
            profile.getExchangeAccess() != null && !profile.getExchangeAccess().isEmpty()
                ? profile.getExchangeAccess()
                : SSOConstants.DEFAULT_EXCHANGE_ACCESS
        );

        userGroup.setValidTill(
            profile.getValidTill() != null
                ? profile.getValidTill()
                : Instant.now().plusSeconds(SSOConstants.DEFAULT_VALID_TILL_DAYS * 24 * 60 * 60)
        );

        userGroup.setLastSyncedAt(Instant.now());

        if (profile.getRegistrations() != null && !profile.getRegistrations().isEmpty()) {
            List<RegistrationDTO> registrations = new ArrayList<>();
            for (Map<String, Object> regMap : profile.getRegistrations()) {
                RegistrationDTO dto = convertMapToRegistrationDTO(regMap);
                registrations.add(dto);
            }
            userGroup.setRegistrations(registrationService.toJsonNode(registrations));
        }
    }

    public Map<String, Object> buildRegistrationsMap(SSOUserGroup userGroup) {
        Map<String, Object> registrationsMap = new HashMap<>();

        try {
            List<Map<String, Object>> portalsList = new ArrayList<>();

            List<RegistrationDTO> registrations = registrationService.parseRegistrations(
                userGroup.getRegistrations()
            );

            for (RegistrationDTO dto : registrations) {
                portalsList.add(convertRegistrationDTOToMap(dto));
            }

            registrationsMap.put("portals", portalsList);
            registrationsMap.put("status", userGroup.getStatus() != null ? userGroup.getStatus() : SSOConstants.DEFAULT_STATUS);
            registrationsMap.put("exchange_access", userGroup.getExchangeAccess() != null ? userGroup.getExchangeAccess() : SSOConstants.DEFAULT_EXCHANGE_ACCESS);

            log.debug("Built registrations map with {} portals for profileId: {}", portalsList.size(), userGroup.getProfileId());

        } catch (Exception e) {
            log.error("Error building registrations map for profileId: {}", userGroup.getProfileId(), e);
            registrationsMap.put("portals", new ArrayList<>());
            registrationsMap.put("status", SSOConstants.DEFAULT_STATUS);
            registrationsMap.put("exchange_access", SSOConstants.DEFAULT_EXCHANGE_ACCESS);
        }

        return registrationsMap;
    }

    public RegistrationDTO convertMapToRegistrationDTO(Map<String, Object> regMap) {
        RegistrationDTO dto = new RegistrationDTO();
        dto.setPortalId((String) regMap.get("portal_id"));
        dto.setPortalName((String) regMap.get("portal_name"));
        dto.setUnitId((String) regMap.get("unit_id"));
        dto.setPortalInternalId((String) regMap.get("portal_internal_id"));
        dto.setUnitAddress((String) regMap.get("unit_address"));
        dto.setState((String) regMap.get("state"));
        dto.setPincode((String) regMap.get("pincode"));
        dto.setRole((String) regMap.get("role"));
        dto.setTypeId((String) regMap.get("type_id"));
        dto.setRegistrationNumber((String) regMap.get("registration_number"));
        dto.setStatus((String) regMap.get("status"));
        dto.setGstNumber((String) regMap.get("gst_number"));

        Object validTill = regMap.get("valid_till");
        if (validTill instanceof Instant) {
            dto.setValidTill((Instant) validTill);
        } else if (validTill instanceof Long) {
            dto.setValidTill(Instant.ofEpochSecond((Long) validTill));
        }

        Object categories = regMap.get("authorized_categories");
        if (categories instanceof List<?>) {
            try {
                List<?> rawList = (List<?>) categories;
                List<String> categoriesList = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String) {
                        categoriesList.add((String) item);
                    }
                }
                dto.setAuthorizedCategories(categoriesList);
            } catch (ClassCastException e) {
                log.warn("Failed to cast authorized_categories to List<String>, skipping");
            }
        }

        Object certCount = regMap.get("certificate_count");
        if (certCount instanceof Integer) {
            dto.setCertificateCount((Integer) certCount);
        }

        return dto;
    }

    public Map<String, Object> convertRegistrationDTOToMap(RegistrationDTO dto) {
        Map<String, Object> map = new HashMap<>();

        map.put("portal_id", dto.getPortalId() != null ? dto.getPortalId() : "");
        map.put("portal_name", dto.getPortalName() != null ? dto.getPortalName() : "");
        map.put("unit_id", dto.getUnitId() != null ? dto.getUnitId() : "");
        map.put("portal_internal_id", dto.getPortalInternalId() != null ? dto.getPortalInternalId() : "");
        map.put("unit_address", dto.getUnitAddress() != null ? dto.getUnitAddress() : "");
        map.put("state", dto.getState() != null ? dto.getState() : "");
        map.put("pincode", dto.getPincode() != null ? dto.getPincode() : "");
        map.put("gst_number", dto.getGstNumber() != null ? dto.getGstNumber() : "");
        map.put("role", dto.getRole() != null ? dto.getRole() : "");
        map.put("type_id", dto.getTypeId() != null ? dto.getTypeId() : "");
        map.put("registration_number", dto.getRegistrationNumber() != null ? dto.getRegistrationNumber() : "");
        map.put("status", dto.getStatus() != null ? dto.getStatus() : SSOConstants.DEFAULT_STATUS);

        if (dto.getValidTill() != null) {
            map.put("valid_till", dto.getValidTill().getEpochSecond());
        } else {
            map.put("valid_till", null);
        }

        map.put("certificate_count", dto.getCertificateCount() != null ? dto.getCertificateCount() : 0);
        map.put("authorized_categories", dto.getAuthorizedCategories() != null ? dto.getAuthorizedCategories() : new ArrayList<>());

        return map;
    }

    private void ensureRequiredFields(SSOUserGroup userGroup, String grpId, String email) {
        if (userGroup.getCompanyName() == null || userGroup.getCompanyName().isEmpty()) {
            userGroup.setCompanyName(generateCompanyName(grpId, email));
        }
        if (userGroup.getCinNumber() == null || userGroup.getCinNumber().isEmpty()) {
            userGroup.setCinNumber(generateCIN(grpId));
        }
        if (userGroup.getGroupPan() == null || userGroup.getGroupPan().isEmpty()) {
            userGroup.setGroupPan(generatePAN(grpId, userGroup.getCompanyName()));
        }
        if (userGroup.getAuthorizedPersonName() == null || userGroup.getAuthorizedPersonName().isEmpty()) {
            userGroup.setAuthorizedPersonName(extractNameFromEmail(email));
        }
        if (userGroup.getEmail() == null || userGroup.getEmail().isEmpty()) {
            userGroup.setEmail(email);
        }
        if (userGroup.getMobile() == null || userGroup.getMobile().isEmpty()) {
            userGroup.setMobile(generateMobile(grpId));
        }
    }

    private String generateCompanyName(String grpId, String email) {
        String domain = "testorg";
        if (email != null && email.contains("@")) {
            domain = email.split("@")[1].split("\\.")[0];
        }
        return capitalizeWords(domain) + " Industries Pvt Ltd";
    }

    private String generateCIN(String grpId) {
        int grpHash = Math.abs(grpId.hashCode() % 1000000);
        return String.format("U%06dDL2020PTC%06d", grpHash, grpHash + 1000);
    }

    private String generatePAN(String grpId, String companyName) {
        int grpHash = Math.abs(grpId.hashCode());
        String initials = extractInitials(companyName);
        return String.format("%s%04dC", initials, grpHash % 10000);
    }

    private String generateMobile(String grpId) {
        int grpHash = Math.abs(grpId.hashCode());
        return String.format("+91%010d", 9000000000L + (grpHash % 999999999));
    }

    private String extractNameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "Authorized Person";
        }
        String localPart = email.split("@")[0];
        String name = localPart.replaceAll("[._-]", " ");
        return capitalizeWords(name);
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split("[\\s_-]+");
        return java.util.Arrays.stream(words)
                .map(word -> word.isEmpty() ? word :
                        Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String extractInitials(String companyName) {
        if (companyName == null || companyName.isEmpty()) return "AAA";
        String[] words = companyName.split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < Math.min(3, words.length); i++) {
            if (!words[i].isEmpty()) {
                initials.append(Character.toUpperCase(words[i].charAt(0)));
            }
        }
        while (initials.length() < 3) initials.append('A');
        return initials.substring(0, 3) + "C" + (Math.abs(companyName.hashCode()) % 10000);
    }
}
