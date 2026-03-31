package com.nexusiam.presentation.controller;

import com.nexusiam.application.dto.request.BulkUserApiMappingRequest;
import com.nexusiam.application.dto.request.PermissionCheckRequest;
import com.nexusiam.application.dto.request.UserApiMappingRequest;
import com.nexusiam.application.dto.response.ApiResponse;
import com.nexusiam.application.dto.response.PermissionCheckResponse;
import com.nexusiam.application.dto.response.UserAccessibleApisResponse;
import com.nexusiam.application.dto.response.UserApiMappingResponse;
import com.nexusiam.application.service.authorization.UserApiMappingService;
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
@RequestMapping("/exchange/v1/int/user-api-mappings")
@RequiredArgsConstructor
@Validated
@Tag(name = "User API Mapping Management", description = "RBAC user-to-API permission mapping endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserApiMappingController {

    private final UserApiMappingService mappingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Create a new user API mapping",
        description = "Assign API access permission to a user"
    )
    public ResponseEntity<ApiResponse<UserApiMappingResponse>> createMapping(
            @Valid @RequestBody UserApiMappingRequest request) {

        log.info("Creating API mapping for user {} to API source {}",
                request.getUserId(), request.getApiSourceId());

        UserApiMappingResponse response = mappingService.createMapping(request);

        ApiResponse<UserApiMappingResponse> apiResponse = new ApiResponse<>(
                "success",
                "User API mapping created successfully",
                response,
                HttpStatus.CREATED.value()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Create multiple user API mappings in bulk",
        description = "Assign multiple API access permissions to a user at once"
    )
    public ResponseEntity<ApiResponse<List<UserApiMappingResponse>>> createBulkMappings(
            @Valid @RequestBody BulkUserApiMappingRequest request) {

        log.info("Creating bulk mappings for user {} - {} API sources",
                request.getUserId(), request.getApiSourceIds().size());

        List<UserApiMappingResponse> responses = mappingService.createBulkMappings(request);

        ApiResponse<List<UserApiMappingResponse>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Created %d out of %d mappings successfully",
                             responses.size(), request.getApiSourceIds().size()),
                responses,
                HttpStatus.CREATED.value()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get all mappings for a user",
        description = "Retrieve all API access permissions assigned to a specific user"
    )
    public ResponseEntity<ApiResponse<List<UserApiMappingResponse>>> getUserMappings(
            @PathVariable @Parameter(description = "User ID") Long userId,
            @RequestParam(defaultValue = "false") @Parameter(description = "Include inactive mappings")
            boolean includeInactive) {

        log.info("Fetching mappings for user {} (includeInactive: {})", userId, includeInactive);

        List<UserApiMappingResponse> mappings = mappingService.getUserMappings(userId, includeInactive);

        ApiResponse<List<UserApiMappingResponse>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Retrieved %d mappings for user", mappings.size()),
                mappings,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/user/{userId}/accessible-apis")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get all accessible APIs for a user",
        description = "Get a summary of all API endpoints the user can access"
    )
    public ResponseEntity<ApiResponse<UserAccessibleApisResponse>> getUserAccessibleApis(
            @PathVariable @Parameter(description = "User ID") Long userId) {

        log.info("Fetching accessible APIs for user {}", userId);

        UserAccessibleApisResponse response = mappingService.getUserAccessibleApis(userId);

        ApiResponse<UserAccessibleApisResponse> apiResponse = new ApiResponse<>(
                "success",
                String.format("User has access to %d API endpoints", response.getTotalApis()),
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{mappingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get mapping by ID",
        description = "Retrieve a specific mapping by its ID"
    )
    public ResponseEntity<ApiResponse<UserApiMappingResponse>> getMappingById(
            @PathVariable @Parameter(description = "Mapping ID") Long mappingId) {

        log.info("Fetching mapping with ID {}", mappingId);

        UserApiMappingResponse response = mappingService.getMappingById(mappingId);

        ApiResponse<UserApiMappingResponse> apiResponse = new ApiResponse<>(
                "success",
                "Mapping retrieved successfully",
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get all mappings for a role",
        description = "Retrieve all API access permissions for users with a specific role"
    )
    public ResponseEntity<ApiResponse<List<UserApiMappingResponse>>> getMappingsByRole(
            @PathVariable @Parameter(description = "User role") String role) {

        log.info("Fetching mappings for role {}", role);

        List<UserApiMappingResponse> mappings = mappingService.getMappingsByRole(role);

        ApiResponse<List<UserApiMappingResponse>> apiResponse = new ApiResponse<>(
                "success",
                String.format("Retrieved %d mappings for role %s", mappings.size(), role),
                mappings,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/user/{userId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Get mapping statistics for a user",
        description = "Get statistics about a user's API access permissions"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserMappingStats(
            @PathVariable @Parameter(description = "User ID") Long userId) {

        log.info("Fetching mapping statistics for user {}", userId);

        Map<String, Object> stats = mappingService.getUserMappingStats(userId);

        ApiResponse<Map<String, Object>> apiResponse = new ApiResponse<>(
                "success",
                "Statistics retrieved successfully",
                stats,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @PutMapping("/{mappingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Update a user API mapping",
        description = "Update the status or role of an existing mapping"
    )
    public ResponseEntity<ApiResponse<UserApiMappingResponse>> updateMapping(
            @PathVariable @Parameter(description = "Mapping ID") Long mappingId,
            @Valid @RequestBody UserApiMappingRequest request) {

        log.info("Updating mapping with ID {}", mappingId);

        UserApiMappingResponse response = mappingService.updateMapping(mappingId, request);

        ApiResponse<UserApiMappingResponse> apiResponse = new ApiResponse<>(
                "success",
                "Mapping updated successfully",
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @PatchMapping("/{mappingId}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Toggle mapping active status",
        description = "Enable or disable a mapping without deleting it"
    )
    public ResponseEntity<ApiResponse<UserApiMappingResponse>> toggleMappingStatus(
            @PathVariable @Parameter(description = "Mapping ID") Long mappingId,
            @RequestParam @Parameter(description = "New active status") boolean isActive) {

        log.info("Toggling mapping {} status to {}", mappingId, isActive);

        UserApiMappingResponse response = mappingService.toggleMappingStatus(mappingId, isActive);

        ApiResponse<UserApiMappingResponse> apiResponse = new ApiResponse<>(
                "success",
                String.format("Mapping %s successfully", isActive ? "enabled" : "disabled"),
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/{mappingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Delete a user API mapping",
        description = "Permanently delete a specific mapping"
    )
    public ResponseEntity<ApiResponse<String>> deleteMapping(
            @PathVariable @Parameter(description = "Mapping ID") Long mappingId) {

        log.info("Deleting mapping with ID {}", mappingId);

        mappingService.deleteMapping(mappingId);

        ApiResponse<String> apiResponse = new ApiResponse<>(
                "success",
                "Mapping deleted successfully",
                null,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Delete all mappings for a user",
        description = "Permanently delete all API access permissions for a user"
    )
    public ResponseEntity<ApiResponse<String>> deleteUserMappings(
            @PathVariable @Parameter(description = "User ID") Long userId) {

        log.info("Deleting all mappings for user {}", userId);

        mappingService.deleteUserMappings(userId);

        ApiResponse<String> apiResponse = new ApiResponse<>(
                "success",
                "All mappings deleted successfully for user",
                null,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Check if user has permission",
        description = "Validate if a user has permission to access a specific API endpoint with a specific HTTP method"
    )
    public ResponseEntity<ApiResponse<PermissionCheckResponse>> checkPermission(
            @Valid @RequestBody PermissionCheckRequest request) {

        log.info("Checking permission for user {} on {} {}",
                request.getUserId(), request.getHttpMethod(), request.getApiPath());

        PermissionCheckResponse response = mappingService.checkPermission(request);

        ApiResponse<PermissionCheckResponse> apiResponse = new ApiResponse<>(
                "success",
                response.getHasPermission() ? "Permission granted" : "Permission denied",
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/check/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    @Operation(
        summary = "Quick permission check",
        description = "Check permission using query parameters (alternative to POST /check)"
    )
    public ResponseEntity<ApiResponse<PermissionCheckResponse>> checkPermissionQuery(
            @PathVariable @Parameter(description = "User ID") Long userId,
            @RequestParam @Parameter(description = "API path") String apiPath,
            @RequestParam @Parameter(description = "HTTP method") String httpMethod) {

        log.info("Quick permission check for user {} on {} {}", userId, httpMethod, apiPath);

        PermissionCheckRequest request = PermissionCheckRequest.builder()
                .userId(userId)
                .apiPath(apiPath)
                .httpMethod(httpMethod)
                .build();

        PermissionCheckResponse response = mappingService.checkPermission(request);

        ApiResponse<PermissionCheckResponse> apiResponse = new ApiResponse<>(
                "success",
                response.getHasPermission() ? "Permission granted" : "Permission denied",
                response,
                HttpStatus.OK.value()
        );

        return ResponseEntity.ok(apiResponse);
    }
}
