package com.nexusiam.presentation.controller;

import com.nexusiam.application.dto.response.ApiResponse;
import com.nexusiam.application.dto.request.ApiSourceRequest;
import com.nexusiam.application.dto.response.ApiSourceResponse;
import com.nexusiam.application.service.api.ApiSourceManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/exchange/v1/int/api-sources")
@RequiredArgsConstructor
@Validated
@Tag(name = "API Source Management", description = "Manage master list of available APIs")
@SecurityRequirement(name = "bearerAuth")
public class ApiSourceController {

    private final ApiSourceManagementService apiSourceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get all API sources",
        description = "Retrieve the complete list of available APIs"
    )
    public ResponseEntity<ApiResponse<List<ApiSourceResponse>>> getAllApiSources() {

        log.info("Fetching all API sources");

        List<ApiSourceResponse> apiSources = apiSourceService.getAllApiSources();

        ApiResponse<List<ApiSourceResponse>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Retrieved %d API sources", apiSources.size()),
                apiSources,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get API source by ID",
        description = "Retrieve a specific API source by its ID"
    )
    public ResponseEntity<ApiResponse<ApiSourceResponse>> getApiSourceById(
            @PathVariable @Parameter(description = "API source ID") Long id) {

        log.info("Fetching API source with ID {}", id);

        ApiSourceResponse response = apiSourceService.getApiSourceById(id);

        ApiResponse<ApiSourceResponse> apiResponse = new ApiResponse<>(
                "success",
                "API source retrieved successfully",
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/path")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get API source by path and method",
        description = "Find an API source by its exact path and HTTP method"
    )
    public ResponseEntity<ApiResponse<ApiSourceResponse>> getApiSourceByPathAndMethod(
            @RequestParam @Parameter(description = "API path") String apiPath,
            @RequestParam @Parameter(description = "HTTP method") String apiMethod) {

        log.info("Fetching API source for {} {}", apiMethod, apiPath);

        ApiSourceResponse response = apiSourceService.getApiSourceByPathAndMethod(apiPath, apiMethod);

        ApiResponse<ApiSourceResponse> apiResponse = new ApiResponse<>(
                "success",
                "API source retrieved successfully",
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/by-path/{apiPath}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get all API sources for a specific path",
        description = "Get all HTTP methods available for a specific API path"
    )
    public ResponseEntity<ApiResponse<List<ApiSourceResponse>>> getApiSourcesByPath(
            @PathVariable @Parameter(description = "API path") String apiPath) {

        log.info("Fetching API sources for path {}", apiPath);

        List<ApiSourceResponse> apiSources = apiSourceService.getApiSourcesByPath(apiPath);

        ApiResponse<List<ApiSourceResponse>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Retrieved %d API sources for path", apiSources.size()),
                apiSources,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/module/{module}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get all API sources for a module",
        description = "Get all APIs belonging to a specific module/microservice"
    )
    public ResponseEntity<ApiResponse<List<ApiSourceResponse>>> getApiSourcesByModule(
            @PathVariable @Parameter(description = "Module name") String module) {

        log.info("Fetching API sources for module {}", module);

        List<ApiSourceResponse> apiSources = apiSourceService.getApiSourcesByModule(module);

        ApiResponse<List<ApiSourceResponse>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Retrieved %d API sources for module %s", apiSources.size(), module),
                apiSources,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Search API sources by path pattern",
        description = "Search for APIs matching a path pattern"
    )
    public ResponseEntity<ApiResponse<List<ApiSourceResponse>>> searchApiSources(
            @RequestParam @Parameter(description = "Path pattern to search") String pathPattern) {

        log.info("Searching API sources with path pattern {}", pathPattern);

        List<ApiSourceResponse> apiSources = apiSourceService.searchApiSourcesByPath(pathPattern);

        ApiResponse<List<ApiSourceResponse>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Found %d matching API sources", apiSources.size()),
                apiSources,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/paths")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get all unique API paths",
        description = "Get a list of all unique API paths in the system"
    )
    public ResponseEntity<ApiResponse<List<String>>> getAllApiPaths() {

        log.info("Fetching all unique API paths");

        List<String> paths = apiSourceService.getAllApiPaths();

        ApiResponse<List<String>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Retrieved %d unique API paths", paths.size()),
                paths,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/modules")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get all unique modules",
        description = "Get a list of all unique modules/microservices"
    )
    public ResponseEntity<ApiResponse<List<String>>> getAllModules() {

        log.info("Fetching all unique modules");

        List<String> modules = apiSourceService.getAllModules();

        ApiResponse<List<String>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Retrieved %d unique modules", modules.size()),
                modules,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get API source statistics",
        description = "Get statistics about the API catalog"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> getApiSourceStats() {

        log.info("Fetching API source statistics");

        Map<String, Object> stats = apiSourceService.getApiSourceStats();

        ApiResponse<Map<String, Object>> apiResponse = new ApiResponse<>(
                "success",
                "Statistics retrieved successfully",
                stats,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Update an API source",
        description = "Update the details of an existing API source"
    )
    public ResponseEntity<ApiResponse<ApiSourceResponse>> updateApiSource(
            @PathVariable @Parameter(description = "API source ID") Long id,
            @Valid @RequestBody ApiSourceRequest request) {

        log.info("Updating API source with ID {}", id);

        ApiSourceResponse response = apiSourceService.updateApiSource(id, request);

        ApiResponse<ApiSourceResponse> apiResponse = new ApiResponse<>(
                "success",
                "API source updated successfully",
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Delete an API source",
        description = "Permanently delete an API source (will also delete all related mappings)"
    )
    public ResponseEntity<ApiResponse<String>> deleteApiSource(
            @PathVariable @Parameter(description = "API source ID") Long id) {

        log.info("Deleting API source with ID {}", id);

        apiSourceService.deleteApiSource(id);

        ApiResponse<String> apiResponse = new ApiResponse<>(
                "success",
                "API source deleted successfully",
                null,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }
}
