package com.nexusiam.application.service.user;

import com.nexusiam.core.domain.entity.UserType;
import com.nexusiam.core.domain.repository.UserTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserRoleMappingService {

    private final UserTypeRepository userTypeRepository;

    private final Map<String, UserType> userTypesByType = new HashMap<>();
    private final Map<String, UserType> userTypesByRole = new HashMap<>();

    private static final String BUYER_TYPE = "buyer";
    private static final String SELLER_TYPE = "seller";
    private static final String ADMIN_TYPE = "admin";

    private static final Map<String, String> EMAIL_TYPE_MAPPING = new HashMap<>();

    static {
        EMAIL_TYPE_MAPPING.put("architsinghania@ioux.in", "admin_dual");
    }

    @PostConstruct
    public void loadUserTypes() {
        log.info("Starting UserTypes cache initialization...");

        try {
            List<UserType> allUserTypes = userTypeRepository.findAll();

            if (allUserTypes.isEmpty()) {
                log.error("No UserTypes found in database! Please check if data.sql has been executed.");
                return;
            }

            log.info("Found {} UserTypes in database", allUserTypes.size());

            for (UserType userType : allUserTypes) {
                if (userType.getType() != null && !userType.getType().isEmpty()) {
                    String normalizedType = userType.getType().toLowerCase().trim();
                    userTypesByType.put(normalizedType, userType);
                    log.debug("Cached UserType by type: '{}' -> UserType(id={}, role='{}')",
                        normalizedType, userType.getId(), userType.getRole());
                }

                if (userType.getRole() != null && !userType.getRole().isEmpty()) {
                    String normalizedRole = userType.getRole().toLowerCase().trim();
                    userTypesByRole.put(normalizedRole, userType);
                    log.debug("Cached UserType by role: '{}' -> UserType(id={}, type='{}')",
                        normalizedRole, userType.getId(), userType.getType());
                }
            }

            log.info("UserTypes cache initialized successfully!");
            log.info("Available types: {}", userTypesByType.keySet());
            log.info("Available roles: {}", userTypesByRole.keySet());

            if (!userTypesByType.containsKey(BUYER_TYPE)) {
                log.error("CRITICAL: 'buyer' type not found in cache!");
            }
            if (!userTypesByType.containsKey(SELLER_TYPE)) {
                log.error("CRITICAL: 'seller' type not found in cache!");
            }

        } catch (Exception e) {
            log.error("CRITICAL: Failed to load UserTypes from database", e);
            throw new RuntimeException("Failed to initialize UserTypes cache", e);
        }
    }

    public List<UserType> getAllUserTypesForUser(String email, List<Map<String, Object>> registrations) {
        log.debug("Getting ALL UserTypes for email: {}", email);

        Set<UserType> userTypes = new LinkedHashSet<>();

        if (userTypesByType.isEmpty() && userTypesByRole.isEmpty()) {
            log.error("UserTypes cache is empty! Attempting to reload...");
            loadUserTypes();

            if (userTypesByType.isEmpty() && userTypesByRole.isEmpty()) {
                throw new RuntimeException("UserTypes cache is empty and reload failed. Check database connectivity and data.sql execution.");
            }
        }

        String hardcodedType = EMAIL_TYPE_MAPPING.get(email);
        if (hardcodedType != null) {
            log.debug("Found hardcoded mapping for email {}: type='{}'", email, hardcodedType);

            if ("admin_dual".equalsIgnoreCase(hardcodedType)) {
                UserType buyerType = userTypesByType.get(BUYER_TYPE);
                UserType sellerType = userTypesByType.get(SELLER_TYPE);

                if (buyerType != null) {
                    userTypes.add(buyerType);
                    log.info("Added BUYER UserType for admin_dual email {}", email);
                }
                if (sellerType != null) {
                    userTypes.add(sellerType);
                    log.info("Added SELLER UserType for admin_dual email {}", email);
                }
            } else {

                UserType userType = userTypesByType.get(hardcodedType.toLowerCase().trim());
                if (userType != null) {
                    userTypes.add(userType);
                    log.info("Added hardcoded UserType for email {}: type='{}', role='{}'",
                        email, userType.getType(), userType.getRole());
                }
            }

            log.info("User {} has hardcoded mapping, skipping registration role extraction", email);
        } else {

            if (registrations != null && !registrations.isEmpty()) {
                List<UserType> extractedTypes = extractAllUserTypesFromRegistrations(registrations);
                userTypes.addAll(extractedTypes);
                log.info("Extracted {} UserType(s) from registrations for email {}",
                    extractedTypes.size(), email);
            }
        }

        if (userTypes.isEmpty()) {
            log.info("No UserTypes found for email {}, defaulting to buyer", email);
            UserType defaultUserType = userTypesByType.get(BUYER_TYPE);

            if (defaultUserType == null) {
                log.error("CRITICAL: Default buyer UserType not found in cache. Available types: {}",
                    userTypesByType.keySet());

                Optional<UserType> buyerTypeOpt = userTypeRepository.findFirstByType(BUYER_TYPE);
                if (buyerTypeOpt.isPresent()) {
                    defaultUserType = buyerTypeOpt.get();
                    userTypesByType.put(BUYER_TYPE, defaultUserType);
                    log.warn("Retrieved buyer type from database as fallback: {}", defaultUserType);
                }
            }

            if (defaultUserType != null) {
                userTypes.add(defaultUserType);
            }
        }

        List<UserType> result = new ArrayList<>(userTypes);
        log.info("Total UserTypes for email {}: {} - Types: {}",
            email, result.size(),
            result.stream().map(UserType::getType).collect(Collectors.toList()));

        return result;
    }

    public UserType getUserTypeForUser(String email, List<Map<String, Object>> registrations) {
        List<UserType> allTypes = getAllUserTypesForUser(email, registrations);

        if (allTypes.isEmpty()) {
            throw new RuntimeException("No UserType found for user");
        }

        UserType sellerType = allTypes.stream()
                .filter(ut -> SELLER_TYPE.equalsIgnoreCase(ut.getType()))
                .findFirst()
                .orElse(allTypes.get(0));

        log.info("Primary UserType for email {}: type='{}', role='{}'",
            email, sellerType.getType(), sellerType.getRole());

        return sellerType;
    }

    private List<UserType> extractAllUserTypesFromRegistrations(List<Map<String, Object>> registrations) {
        Set<UserType> userTypes = new LinkedHashSet<>();

        for (Map<String, Object> reg : registrations) {
            if (!isActiveRegistration(reg)) {
                continue;
            }

            UserType userType = extractUserTypeFromSingleRegistration(reg);
            if (userType != null) {
                userTypes.add(userType);
            }
        }

        return new ArrayList<>(userTypes);
    }

    private UserType extractUserTypeFromSingleRegistration(Map<String, Object> reg) {
        Object roleObj = reg.get("role");
        if (roleObj == null) {
            log.debug("Registration has no role field, skipping");
            return null;
        }

        String registrationRole = String.valueOf(roleObj).trim();
        log.debug("Processing registration role: '{}'", registrationRole);

        String normalizedRole = registrationRole.toLowerCase().trim();
        UserType userType = userTypesByRole.get(normalizedRole);

        if (userType != null) {
            log.debug("Found UserType by direct role match: '{}' -> type='{}'",
                userType.getRole(), userType.getType());
            return userType;
        }

        log.debug("No direct match for role '{}', trying fuzzy match", normalizedRole);

        userType = fuzzyMatchUserType(normalizedRole);
        if (userType != null) {
            log.debug("Found UserType by fuzzy match: '{}' -> type='{}'",
                userType.getRole(), userType.getType());
            return userType;
        }

        log.debug("No fuzzy match found for role '{}'", normalizedRole);
        return null;
    }

    private boolean isActiveRegistration(Map<String, Object> registration) {
        Object statusObj = registration.get("status");
        boolean isActive = "active".equalsIgnoreCase(String.valueOf(statusObj)) ||
                          "Active".equalsIgnoreCase(String.valueOf(statusObj));
        log.debug("Registration status: {}, isActive: {}", statusObj, isActive);
        return isActive;
    }

    private UserType fuzzyMatchUserType(String normalizedRole) {
        for (Map.Entry<String, UserType> entry : userTypesByRole.entrySet()) {
            String cachedRole = entry.getKey();

            if (normalizedRole.contains(cachedRole) || cachedRole.contains(normalizedRole)) {
                log.debug("Fuzzy match found: '{}' matches '{}'", normalizedRole, cachedRole);
                return entry.getValue();
            }
        }

        if (normalizedRole.contains("pibo")) {
            return findByRolePattern("pibo");
        }
        if (normalizedRole.contains("brand")) {
            return findByRolePattern("brand");
        }
        if (normalizedRole.contains("recycl")) {
            return findByRolePattern("recycl");
        }
        if (normalizedRole.contains("producer")) {
            return findByRolePattern("producer");
        }
        if (normalizedRole.contains("importer")) {
            return findByRolePattern("importer");
        }
        if (normalizedRole.contains("facility")) {
            return findByRolePattern("facility");
        }
        if (normalizedRole.contains(ADMIN_TYPE)) {
            return findByRolePattern(ADMIN_TYPE);
        }

        return null;
    }

    private UserType findByRolePattern(String pattern) {
        for (Map.Entry<String, UserType> entry : userTypesByRole.entrySet()) {
            if (entry.getKey().contains(pattern)) {
                log.debug("Pattern match found: pattern '{}' matches role '{}'", pattern, entry.getKey());
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean hasHardcodedMapping(String email) {
        return EMAIL_TYPE_MAPPING.containsKey(email);
    }

    public String getHardcodedType(String email) {
        return EMAIL_TYPE_MAPPING.get(email);
    }

    public void refreshCache() {
        log.info("Refreshing UserTypes cache...");
        userTypesByType.clear();
        userTypesByRole.clear();
        loadUserTypes();
    }

    public UserType getUserTypeByType(String type) {
        if (type == null) return null;
        return userTypesByType.get(type.toLowerCase().trim());
    }

    public UserType getUserTypeByRole(String role) {
        if (role == null) return null;
        return userTypesByRole.get(role.toLowerCase().trim());
    }

    public Map<String, UserType> getAllUserTypesByType() {
        return new HashMap<>(userTypesByType);
    }

    public Map<String, UserType> getAllUserTypesByRole() {
        return new HashMap<>(userTypesByRole);
    }

    public List<String> getAvailableRoleStrings(String email, List<Map<String, Object>> registrations) {
        List<UserType> userTypes = getAllUserTypesForUser(email, registrations);

        return userTypes.stream()
                .map(UserType::getType)
                .filter(Objects::nonNull)
                .map(type -> type.equalsIgnoreCase(BUYER_TYPE) ? "BUYER" :
                            type.equalsIgnoreCase(SELLER_TYPE) ? "SELLER" : type.toUpperCase())
                .distinct()
                .collect(Collectors.toList());
    }
}
