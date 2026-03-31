package com.nexusiam.application.service.enricher;

import com.nexusiam.application.dto.response.SSOProfileResponse;
import com.nexusiam.application.service.user.UserRoleMappingService;
import com.nexusiam.application.service.utility.IdGeneratorService;
import com.nexusiam.core.domain.entity.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileDataEnricher implements ProfileDataEnricherHardcode {

    private final IdGeneratorService idGeneratorService;
    private final UserRoleMappingService userRoleMappingService;

    private static final List<String> COMPANY_NAMES = List.of(
        "GreenTech Recycling Pvt Ltd",
        "EcoSolutions Industries Ltd",
        "Sustainable Waste Management Ltd",
        "CleanEarth Processors Pvt Ltd",
        "RecycleHub Technologies Ltd",
        "WasteToWealth Industries Pvt Ltd"
    );

    private static final List<String> PERSON_NAMES = List.of(
        "Rajesh Kumar",
        "Priya Sharma",
        "Amit Patel",
        "Sunita Verma",
        "Vikram Singh",
        "Anita Desai"
    );

    private static final List<String> DESIGNATIONS = List.of(
        "Managing Director",
        "Chief Executive Officer",
        "Director Operations",
        "General Manager",
        "Plant Head"
    );

    private static final List<String> CITIES = List.of(
        "Gurugram", "Noida", "Mumbai", "Pune", "Bangalore", "Chennai", "Hyderabad"
    );

    private static final List<String> STATES = List.of(
        "Haryana", "Uttar Pradesh", "Maharashtra", "Karnataka", "Tamil Nadu", "Telangana"
    );

    public void enrichWithTestData(SSOProfileResponse profile) {
        String grpId = profile.getGrpId();
        if (grpId == null || grpId.isEmpty()) {
            log.warn("grpId is null/empty in enrichWithTestData, this should not happen");
            grpId = "GRP-000000";
        }
        int seed = Math.abs(grpId.hashCode());

        String companyName = COMPANY_NAMES.get(seed % COMPANY_NAMES.size());
        profile.setCompanyName(companyName);

        int cinBase = 10000 + (seed % 90000);
        String stateCode = getStateCodeByIndex(seed);
        profile.setCinNumber(String.format("U%05d%s2019PTC%06d", cinBase, stateCode, seed % 1000000));

        String panPrefix = getCompanyInitials(companyName);
        profile.setPanNumber(String.format("%s%04d%c", panPrefix, seed % 10000, (char)('A' + (seed % 26))));

        profile.setAuthorizedPersonName(PERSON_NAMES.get(seed % PERSON_NAMES.size()));
        profile.setDesignation(DESIGNATIONS.get(seed % DESIGNATIONS.size()));

        profile.setMobile(String.format("9%09d", seed % 1000000000));
        profile.setLandline(String.format("0%03d-%07d",
            120 + (seed % 80), 2000000 + (seed % 8000000)));

        profile.setStatus("Active");
        profile.setComplianceStatus("active");
        profile.setExchangeAccess("allowed");
        profile.setValidTill(Instant.now().plusSeconds(365L * 24 * 60 * 60));

        log.info("Enriched profile with test data - grpId: {}, profileId: {}, Company: {}, CIN: {}, PAN: {}",
                grpId, profile.getProfileId(), companyName, profile.getCinNumber(), profile.getPanNumber());
    }

    public List<Map<String, Object>> generateTestRegistrations(String grpId, String email) {
        if (grpId == null || grpId.isEmpty()) {
            log.warn("grpId is null/empty in generateTestRegistrations, using default");
            grpId = "GRP-000000";
        }
        int seed = Math.abs(grpId.hashCode());

        String hardcodedType = userRoleMappingService.getHardcodedType(email);

        List<Map<String, Object>> registrations = new ArrayList<>();

        if (hardcodedType != null) {

            if ("admin_dual".equalsIgnoreCase(hardcodedType)) {
                log.info("Generating DUAL (buyer+seller) registrations for admin email: {}", email);

                registrations.addAll(generateRegistrationsForType(grpId, seed, "buyer"));

                registrations.addAll(generateRegistrationsForType(grpId, seed + 5000, "seller"));
                log.info("Generated {} total registrations (buyer+seller) for admin_dual: {}",
                    registrations.size(), email);
            } else {
                log.info("Generating registrations for hardcoded type '{}' for email: {}", hardcodedType, email);
                registrations.addAll(generateRegistrationsForType(grpId, seed, hardcodedType));
            }
        } else {
            log.info("Generating mixed registrations for email: {}", email);
            int registrationCount = 1 + (seed % 2);

            registrations.add(createBatteryRegistration(grpId, seed, 1));

            if (registrationCount > 1) {
                registrations.add(createPlasticRegistration(grpId, seed, 2));
            }
        }

        log.info("Generated {} realistic test registrations for grpId: {}, email: {}",
            registrations.size(), grpId, email);
        return registrations;
    }

    private List<Map<String, Object>> generateRegistrationsForType(String grpId, int seed, String userType) {
        List<Map<String, Object>> registrations = new ArrayList<>();

        List<String> matchingRoles = getMatchingRoles(userType);

        if (matchingRoles.isEmpty()) {
            log.warn("No matching roles found for type: {}, generating default registration", userType);
            registrations.add(createBatteryRegistration(grpId, seed, 1));
            return registrations;
        }

        int registrationCount = 1 + (seed % 2);
        for (int i = 0; i < registrationCount; i++) {
            String role = matchingRoles.get(i % matchingRoles.size());

            if (i % 2 == 0) {
                registrations.add(createBatteryRegistrationWithRole(grpId, seed, i + 1, role));
            } else {
                registrations.add(createPlasticRegistrationWithRole(grpId, seed, i + 1, role));
            }
        }

        return registrations;
    }

    private List<String> getMatchingRoles(String userType) {
        List<String> matchingRoles = new ArrayList<>();

        UserType userTypeEntity = userRoleMappingService.getUserTypeByType(userType);
        if (userTypeEntity != null) {
            matchingRoles.add(userTypeEntity.getRole());
        }

        if ("buyer".equalsIgnoreCase(userType)) {
            matchingRoles.add("PIBOs");
            matchingRoles.add("Brand Owners");
        }
        else if ("seller".equalsIgnoreCase(userType)) {
            matchingRoles.add("Producers");
            matchingRoles.add("Importers");
            matchingRoles.add("Recyclers");
            matchingRoles.add("Recycling Facility");
        }
        else if ("admin".equalsIgnoreCase(userType)) {
            matchingRoles.add("Administrators");
        }

        return matchingRoles;
    }

    private Map<String, Object> createBatteryRegistration(String grpId, int seed, int sequence) {
        int unitSeed = seed + sequence;
        String[] roles = {"Recycling Facility", "Recyclers", "Producers", "PIBOs"};
        String role = roles[unitSeed % roles.length];
        return createBatteryRegistrationWithRole(grpId, seed, sequence, role);
    }

    private Map<String, Object> createBatteryRegistrationWithRole(String grpId, int seed, int sequence, String role) {
        Map<String, Object> reg = new HashMap<>();
        int unitSeed = seed + sequence;

        reg.put("portal_id", "battery");
        reg.put("portal_name", "Battery Waste Management Portal");
        reg.put("unit_id", idGeneratorService.generateBatteryUnitId(unitSeed));

        String city = CITIES.get(unitSeed % CITIES.size());
        reg.put("unit_address", String.format("Plot %d, Industrial Area, Phase II, %s",
            10 + (unitSeed % 90), city));

        String state = STATES.get(unitSeed % STATES.size());
        reg.put("state", state);
        reg.put("pincode", String.format("%06d", 100000 + (unitSeed % 900000)));

        int gstStateCode = 1 + (unitSeed % 35);
        reg.put("gst_number", String.format("%02dAABC%04dD1Z%d",
            gstStateCode, unitSeed % 10000, unitSeed % 10));

        reg.put("role", role);

        reg.put("type_id", String.format("BAT_%s_%d",
            role.replaceAll("[^A-Z]", "").substring(0, Math.min(2, role.replaceAll("[^A-Z]", "").length())),
            unitSeed % 10));

        reg.put("registration_number", String.format("BAT/%s/2024/%04d",
            getStateCodeByIndex(unitSeed), unitSeed % 10000));
        reg.put("status", "Active");
        reg.put("valid_till", null);
        reg.put("portal_internal_id", idGeneratorService.generateInternalPortalId("BAT", unitSeed));
        reg.put("authorized_categories", List.of("ALL"));
        reg.put("certificate_count", 1000 + (unitSeed % 2000));

        return reg;
    }

    private Map<String, Object> createPlasticRegistration(String grpId, int seed, int sequence) {
        int unitSeed = seed + sequence + 1000;
        String[] roles = {"Recyclers", "Producers", "Brand Owners", "Importers"};
        String role = roles[unitSeed % roles.length];
        return createPlasticRegistrationWithRole(grpId, seed, sequence, role);
    }

    private Map<String, Object> createPlasticRegistrationWithRole(String grpId, int seed, int sequence, String role) {
        Map<String, Object> reg = new HashMap<>();
        int unitSeed = seed + sequence + 1000;

        reg.put("portal_id", "plastic");
        reg.put("portal_name", "Plastic Waste Management Portal");
        reg.put("unit_id", idGeneratorService.generatePlasticUnitId(unitSeed));

        String city = CITIES.get(unitSeed % CITIES.size());
        reg.put("unit_address", String.format("Plot %d, Industrial Area, Phase II, %s",
            10 + (unitSeed % 90), city));

        String state = STATES.get(unitSeed % STATES.size());
        reg.put("state", state);
        reg.put("pincode", String.format("%06d", 100000 + (unitSeed % 900000)));

        int gstStateCode = 1 + (unitSeed % 35);
        reg.put("gst_number", String.format("%02dAABC%04dD1Z%d",
            gstStateCode, unitSeed % 10000, unitSeed % 10));

        reg.put("role", role);

        reg.put("type_id", String.format("PL_%s_%d",
            role.replaceAll("[^A-Z]", "").substring(0, Math.min(2, role.replaceAll("[^A-Z]", "").length())),
            unitSeed % 10));

        reg.put("registration_number", String.format("PL/%s/2024/%04d",
            getStateCodeByIndex(unitSeed), unitSeed % 10000));
        reg.put("status", "Active");
        reg.put("valid_till", null);
        reg.put("portal_internal_id", idGeneratorService.generateInternalPortalId("PL", unitSeed));
        reg.put("authorized_categories", List.of("Rigid Plastic", "MLP"));
        reg.put("certificate_count", 300 + (unitSeed % 500));

        return reg;
    }

    private String getStateCodeByIndex(int seed) {
        List<String> codes = List.of("HR", "UP", "MH", "KA", "TN", "TG", "DL", "GJ");
        return codes.get(Math.abs(seed) % codes.size());
    }

    private String getCompanyInitials(String companyName) {
        if (companyName == null || companyName.isEmpty()) return "AABC";

        String[] words = companyName.split("\\s+");
        StringBuilder initials = new StringBuilder();

        for (int i = 0; i < Math.min(3, words.length); i++) {
            if (!words[i].isEmpty() && !words[i].equalsIgnoreCase("Pvt") &&
                !words[i].equalsIgnoreCase("Ltd")) {
                initials.append(Character.toUpperCase(words[i].charAt(0)));
            }
        }

        while (initials.length() < 4) {
            initials.append('A');
        }

        return initials.substring(0, 4) + "C";
    }

    public String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split("[\\s_-]+");
        return Arrays.stream(words)
                .map(word -> word.isEmpty() ? word :
                        Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
