package com.nexusiam.application.service.api;

import com.nexusiam.application.dto.request.ApiSourceRequest;
import com.nexusiam.application.dto.response.ApiSourceResponse;
import com.nexusiam.core.domain.entity.ApiSource;
import com.nexusiam.core.domain.repository.ApiSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiSourceManagementService {

    private final ApiSourceRepository apiSourceRepository;

    @Transactional
    public ApiSourceResponse createApiSource(ApiSourceRequest request) {
        log.info("Creating API source for {} {}", request.getApiMethod(), request.getApiPath());

        if (apiSourceRepository.existsByApiPathAndApiMethod(request.getApiPath(), request.getApiMethod())) {
            throw new RuntimeException("API source already exists for this path and method. Use update instead.");
        }

        ApiSource apiSource = Objects.requireNonNull(
            ApiSource.builder()
                .apiPath(request.getApiPath())
                .apiMethod(request.getApiMethod().toUpperCase())
                .module(request.getModule())
                .description(request.getDescription())
                .build(),
                "ApiSource builder returned null"
        );

        ApiSource saved = Objects.requireNonNull(
            apiSourceRepository.save(apiSource),
            "Repository returned null while saving ApiSource"
        );

        log.info("Created API source with ID {}", saved.getId());

        return mapToResponse(saved);
    }

    public List<ApiSourceResponse> createBulkApiSources(List<ApiSourceRequest> requests) {

        List<ApiSourceResponse> result = new java.util.ArrayList<>();

        for (ApiSourceRequest request : requests) {
            if (apiSourceRepository.existsByApiPathAndApiMethod(request.getApiPath(), request.getApiMethod())) {
                log.warn("API source already exists for {} {}, skipping",
                        request.getApiMethod(), request.getApiPath());
                continue;
            }

            try {
                ApiSource apiSource = ApiSource.builder()
                        .apiPath(request.getApiPath())
                        .apiMethod(request.getApiMethod().toUpperCase())
                        .module(request.getModule())
                        .description(request.getDescription())
                        .build();

                if (apiSource == null) {
                    log.error("apiSource returned null entity");
                    continue;
                }

                result.add(mapToResponse(apiSourceRepository.save(apiSource)));

            } catch (Exception e) {
                log.error("Error creating API source for {} {}: {}",
                        request.getApiMethod(), request.getApiPath(), e.getMessage());
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<ApiSourceResponse> getAllApiSources() {
        log.debug("Fetching all API sources");

        return apiSourceRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiSourceResponse getApiSourceById(Long id) {
        Objects.requireNonNull(id, "ID cannot be null");
        log.debug("Fetching API source with ID {}", id);

        ApiSource apiSource = apiSourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API source not found with ID: " + id));

        return mapToResponse(apiSource);
    }

    @Transactional(readOnly = true)
    public ApiSourceResponse getApiSourceByPathAndMethod(String apiPath, String apiMethod) {
        log.debug("Fetching API source for {} {}", apiMethod, apiPath);

        ApiSource apiSource = apiSourceRepository.findByApiPathAndApiMethod(apiPath, apiMethod.toUpperCase())
                .orElseThrow(() -> new RuntimeException("API source not found for " + apiMethod + " " + apiPath));

        return mapToResponse(apiSource);
    }

    @Transactional(readOnly = true)
    public List<ApiSourceResponse> getApiSourcesByPath(String apiPath) {
        log.debug("Fetching API sources for path {}", apiPath);

        return apiSourceRepository.findByApiPath(apiPath).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ApiSourceResponse> getApiSourcesByModule(String module) {
        log.debug("Fetching API sources for module {}", module);

        return apiSourceRepository.findByModule(module).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ApiSourceResponse> searchApiSourcesByPath(String pathPattern) {
        log.debug("Searching API sources with path pattern {}", pathPattern);

        return apiSourceRepository.searchByPathPattern(pathPattern).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getAllApiPaths() {
        log.debug("Fetching all unique API paths");
        return apiSourceRepository.findAllDistinctApiPaths();
    }

    @Transactional(readOnly = true)
    public List<String> getAllModules() {
        log.debug("Fetching all unique modules");
        return apiSourceRepository.findAllDistinctModules();
    }

    @Transactional
    public ApiSourceResponse updateApiSource(Long id, ApiSourceRequest request) {
        log.info("Updating API source with ID {}", id);

        Objects.requireNonNull(id, "ID cannot be null");

        ApiSource apiSource = apiSourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API source not found with ID: " + id));

        apiSource.setApiPath(request.getApiPath());
        apiSource.setApiMethod(request.getApiMethod().toUpperCase());
        apiSource.setModule(request.getModule());
        apiSource.setDescription(request.getDescription());

        ApiSource updated = apiSourceRepository.save(apiSource);
        log.info("Updated API source with ID {}", updated.getId());

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteApiSource(Long id) {
        log.info("Deleting API source with ID {}", id);

        Objects.requireNonNull(id, "ID cannot be null");

        if (!apiSourceRepository.existsById(id)) {
            throw new RuntimeException("API source not found with ID: " + id);
        }

        apiSourceRepository.deleteById(id);
        log.info("Deleted API source with ID {}", id);
    }

    private ApiSourceResponse mapToResponse(ApiSource apiSource) {
        return ApiSourceResponse.builder()
                .id(apiSource.getId())
                .apiPath(apiSource.getApiPath())
                .apiMethod(apiSource.getApiMethod())
                .module(apiSource.getModule())
                .description(apiSource.getDescription())
                .createdDate(apiSource.getCreatedDate())
                .updatedDate(apiSource.getUpdatedDate())
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getApiSourceStats() {
        long totalApis = apiSourceRepository.count();
        List<String> modules = apiSourceRepository.findAllDistinctModules();
        List<String> paths = apiSourceRepository.findAllDistinctApiPaths();

        return java.util.Map.of(
                "totalApis", totalApis,
                "totalModules", modules.size(),
                "totalUniquePaths", paths.size(),
                "modules", modules
        );
    }
}
