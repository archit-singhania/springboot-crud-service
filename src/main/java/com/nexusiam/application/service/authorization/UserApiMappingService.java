package com.nexusiam.application.service.authorization;

import com.nexusiam.application.dto.request.BulkUserApiMappingRequest;
import com.nexusiam.application.dto.request.PermissionCheckRequest;
import com.nexusiam.application.dto.request.UserApiMappingRequest;
import com.nexusiam.application.dto.response.PermissionCheckResponse;
import com.nexusiam.application.dto.response.UserAccessibleApisResponse;
import com.nexusiam.application.dto.response.UserApiMappingResponse;
import com.nexusiam.core.domain.entity.ApiSource;
import com.nexusiam.core.domain.entity.InternalUser;
import com.nexusiam.core.domain.entity.UserApiMapping;
import com.nexusiam.core.domain.repository.ApiSourceRepository;
import com.nexusiam.core.domain.repository.InternalUserRepository;
import com.nexusiam.core.domain.repository.UserApiMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserApiMappingService {

    private final UserApiMappingRepository mappingRepository;
    private final InternalUserRepository userRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final PermissionCacheService cacheService;

    @Transactional
    public UserApiMappingResponse createMapping(UserApiMappingRequest request) {
        log.info("Creating API mapping for user {} to API source {}",
                request.getUserId(), request.getApiSourceId());

        Long userId = Objects.requireNonNull(request.getUserId(), "UserId cannot be null");
        InternalUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new RuntimeException("Cannot assign permissions to deleted user");
        }

        Long sourceId = Objects.requireNonNull(request.getApiSourceId(), "sourceId cannot be null");

        ApiSource apiSource = apiSourceRepository.findById(sourceId)
                .orElseThrow(() -> new RuntimeException("Source not found with ID: " + sourceId));

        if (mappingRepository.existsByUserIdAndApiSourceId(request.getUserId(), request.getApiSourceId())) {
            throw new RuntimeException("Mapping already exists for this user and API source. Use update instead.");
        }

        UserApiMapping mapping = Objects.requireNonNull(UserApiMapping.builder()
                .userId(request.getUserId())
                .userRole(request.getUserRole() != null ? request.getUserRole() : user.getRole())
                .apiSourceId(request.getApiSourceId())
                .isActive(request.getIsActive())
                .build()
        );

        UserApiMapping saved = mappingRepository.save(mapping);
        log.info("Created mapping with ID {} for user {}", saved.getId(), saved.getUserId());

        cacheService.invalidateUser(saved.getUserId());

        return mapToResponse(saved, user, apiSource);
    }

    @Transactional
    public List<UserApiMappingResponse> createBulkMappings(BulkUserApiMappingRequest request) {
        log.info("Creating bulk mappings for user {} - {} API sources",
                request.getUserId(), request.getApiSourceIds().size());

        InternalUser user = userRepository.findById(Objects.requireNonNull(request.getUserId()))
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + request.getUserId()));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new RuntimeException("Cannot assign permissions to deleted user");
        }

        List<UserApiMappingResponse> responses = new ArrayList<>();

        for (Long apiSourceId : request.getApiSourceIds()) {
            try {

                ApiSource apiSource = apiSourceRepository.findById(Objects.requireNonNull(apiSourceId))
                        .orElseThrow(() -> new RuntimeException("API source not found with ID: " + apiSourceId));

                if (mappingRepository.existsByUserIdAndApiSourceId(request.getUserId(), apiSourceId)) {
                    log.warn("Mapping already exists for user {} and API source {}, skipping",
                            request.getUserId(), apiSourceId);
                    continue;
                }

                UserApiMapping mapping = Objects.requireNonNull(UserApiMapping.builder()
                        .userId(request.getUserId())
                        .userRole(request.getUserRole() != null ? request.getUserRole() : user.getRole())
                        .apiSourceId(apiSourceId)
                        .isActive(request.getIsActive())
                        .build()
                );

                UserApiMapping saved = mappingRepository.save(mapping);
                responses.add(mapToResponse(saved, user, apiSource));

            } catch (Exception e) {
                log.error("Error creating mapping for API source {}: {}", apiSourceId, e.getMessage());

            }
        }

        log.info("Created {} out of {} mappings for user {}",
                responses.size(), request.getApiSourceIds().size(), request.getUserId());

        if (!responses.isEmpty()) {
            cacheService.invalidateUser(request.getUserId());
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<UserApiMappingResponse> getUserMappings(Long userId, boolean includeInactive) {
        log.debug("Fetching mappings for user {} (includeInactive: {})", userId, includeInactive);

        InternalUser user = userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        List<UserApiMapping> mappings = includeInactive
                ? mappingRepository.findByUserId(userId)
                : mappingRepository.findByUserIdAndIsActiveTrue(userId);

        return mappings.stream()
                .map(m -> {
                    ApiSource apiSource = apiSourceRepository.findById(Objects.requireNonNull(m.getApiSourceId())).orElse(null);
                    return mapToResponse(m, user, apiSource);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserApiMappingResponse getMappingById(Long mappingId) {
        log.debug("Fetching mapping with ID {}", mappingId);

        UserApiMapping mapping = mappingRepository.findById(Objects.requireNonNull(mappingId))
                .orElseThrow(() -> new RuntimeException("Mapping not found with ID: " + mappingId));

        InternalUser user = userRepository.findById(Objects.requireNonNull(mapping.getUserId())).orElse(null);
        ApiSource apiSource = apiSourceRepository.findById(Objects.requireNonNull(mapping.getApiSourceId())).orElse(null);

        return mapToResponse(mapping, user, apiSource);
    }

    @Transactional(readOnly = true)
    public UserAccessibleApisResponse getUserAccessibleApis(Long userId) {
        log.debug("Fetching accessible APIs for user {}", userId);

        InternalUser user = userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        List<UserApiMapping> mappings = mappingRepository.findUserAccessibleApisWithDetails(userId);

        List<UserAccessibleApisResponse.ApiAccessInfo> accessibleApis = mappings.stream()
                .map(m -> {
                    ApiSource apiSource = apiSourceRepository.findById(m.getApiSourceId()).orElse(null);
                    if (apiSource == null) {
                        return null;
                    }
                    return UserAccessibleApisResponse.ApiAccessInfo.builder()
                            .apiPath(apiSource.getApiPath())
                            .allowedMethods(List.of(apiSource.getApiMethod()))
                            .module(apiSource.getModule())
                            .description(apiSource.getDescription())
                            .isActive(m.getIsActive())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return UserAccessibleApisResponse.builder()
                .userId(userId)
                .userName(user.getName())
                .userEmail(user.getEmail())
                .userRole(user.getRole())
                .totalApis(accessibleApis.size())
                .accessibleApis(accessibleApis)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserApiMappingResponse> getMappingsByRole(String role) {
        log.debug("Fetching mappings for role {}", role);

        List<UserApiMapping> mappings = mappingRepository.findByUserRoleAndIsActiveTrue(role);

        return mappings.stream()
                .map(m -> {
                    InternalUser user = userRepository.findById(Objects.requireNonNull(m.getUserId())).orElse(null);
                    ApiSource apiSource = apiSourceRepository.findById(Objects.requireNonNull(m.getApiSourceId())).orElse(null);
                    return mapToResponse(m, user, apiSource);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public UserApiMappingResponse updateMapping(Long mappingId, UserApiMappingRequest request) {
        log.info("Updating mapping with ID {}", mappingId);

        UserApiMapping mapping = mappingRepository.findById(Objects.requireNonNull(mappingId))
                .orElseThrow(() -> new RuntimeException("Mapping not found with ID: " + mappingId));

        mapping.setIsActive(request.getIsActive());
        if (request.getUserRole() != null) {
            mapping.setUserRole(request.getUserRole());
        }

        UserApiMapping updated = mappingRepository.save(mapping);
        log.info("Updated mapping with ID {}", updated.getId());

        cacheService.invalidateUser(updated.getUserId());

        InternalUser user = userRepository.findById(Objects.requireNonNull(mapping.getUserId())).orElse(null);
        ApiSource apiSource = apiSourceRepository.findById(Objects.requireNonNull(mapping.getApiSourceId())).orElse(null);
        return mapToResponse(updated, user, apiSource);
    }

    @Transactional
    public UserApiMappingResponse toggleMappingStatus(Long mappingId, boolean isActive) {
        log.info("Toggling mapping {} status to {}", mappingId, isActive);

        UserApiMapping mapping = mappingRepository.findById(Objects.requireNonNull(mappingId))
                .orElseThrow(() -> new RuntimeException("Mapping not found with ID: " + mappingId));

        mapping.setIsActive(isActive);
        UserApiMapping updated = mappingRepository.save(mapping);

        cacheService.invalidateUser(updated.getUserId());

        InternalUser user = userRepository.findById(Objects.requireNonNull(mapping.getUserId())).orElse(null);
        ApiSource apiSource = apiSourceRepository.findById(Objects.requireNonNull(mapping.getApiSourceId())).orElse(null);
        return mapToResponse(updated, user, apiSource);
    }

    @Transactional
    public void deleteMapping(Long mappingId) {
        log.info("Deleting mapping with ID {}", mappingId);

        UserApiMapping mapping = mappingRepository.findById(Objects.requireNonNull(mappingId))
                .orElseThrow(() -> new RuntimeException("Mapping not found with ID: " + mappingId));

        Long userId = mapping.getUserId();
        mappingRepository.deleteById(mappingId);
        log.info("Deleted mapping with ID {}", mappingId);

        cacheService.invalidateUser(userId);
    }

    @Transactional
    public void deleteUserMappings(Long userId) {
        log.info("Deleting all mappings for user {}", userId);

        mappingRepository.deleteByUserId(userId);
        log.info("Deleted all mappings for user {}", userId);

        cacheService.invalidateUser(userId);
    }

    @Transactional(readOnly = true)
    public PermissionCheckResponse checkPermission(PermissionCheckRequest request) {
        log.debug("Checking permission for user {} on {} {}",
                request.getUserId(), request.getHttpMethod(), request.getApiPath());

        if (!userRepository.existsById(Objects.requireNonNull(request.getUserId()))) {
            return PermissionCheckResponse.denied(
                    request.getUserId(),
                    request.getApiPath(),
                    request.getHttpMethod(),
                    "User not found"
            );
        }

        boolean hasPermission = mappingRepository.hasPermission(
                request.getUserId(),
                request.getApiPath(),
                request.getHttpMethod().toUpperCase()
        );

        if (hasPermission) {
            return PermissionCheckResponse.allowed(
                    request.getUserId(),
                    request.getApiPath(),
                    request.getHttpMethod()
            );
        } else {
            return PermissionCheckResponse.denied(
                    request.getUserId(),
                    request.getApiPath(),
                    request.getHttpMethod(),
                    "User does not have permission for this API and method"
            );
        }
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, String apiPath, String httpMethod) {
        return mappingRepository.hasPermission(userId, apiPath, httpMethod.toUpperCase());
    }

    private UserApiMappingResponse mapToResponse(UserApiMapping mapping, InternalUser user, ApiSource apiSource) {
        return UserApiMappingResponse.builder()
                .id(mapping.getId())
                .userId(mapping.getUserId())
                .userRole(mapping.getUserRole())
                .userName(user != null ? user.getName() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .apiSourceId(mapping.getApiSourceId())
                .apiPath(apiSource != null ? apiSource.getApiPath() : null)
                .apiMethod(apiSource != null ? apiSource.getApiMethod() : null)
                .module(apiSource != null ? apiSource.getModule() : null)
                .description(apiSource != null ? apiSource.getDescription() : null)
                .isActive(mapping.getIsActive())
                .createdDate(mapping.getCreatedDate())
                .updatedDate(mapping.getUpdatedDate())
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getUserMappingStats(Long userId) {
        long totalMappings = mappingRepository.countByUserIdAndIsActiveTrue(userId);
        List<Object[]> moduleStats = mappingRepository.countUserPermissionsByModule(userId);

        return java.util.Map.of(
                "userId", userId,
                "totalActiveMappings", totalMappings,
                "permissionsByModule", moduleStats
        );
    }
}
