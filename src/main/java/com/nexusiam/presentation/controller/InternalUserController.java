package com.nexusiam.presentation.controller;

import com.nexusiam.application.dto.response.ApiResponse;
import com.nexusiam.application.dto.PageInfo;
import com.nexusiam.application.dto.request.InternalUserRequest;
import com.nexusiam.application.dto.response.InternalUserResponse;
import com.nexusiam.application.service.user.InternalUserService;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/exchange/v1/int/users")
@Validated
@Tag(name = "User Management", description = "User management APIs for internal users")
public class InternalUserController {

    private final InternalUserService userService;

    private static final int DEFAULT_PAGE_SIZE = 5;

    public InternalUserController(InternalUserService userService) {
        this.userService = userService;
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<InternalUserResponse>> create(@Valid @RequestBody InternalUserRequest req) {
        InternalUserResponse response = userService.createUser(req);
        ApiResponse<InternalUserResponse> apiResponse = new ApiResponse<>(
                "success",
                "User created successfully",
                response,
                HttpStatus.CREATED.value()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<List<InternalUserResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page
    ) {
        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE, Sort.by("id").ascending());
        Page<InternalUserResponse> userPage = userService.getAllUsers(pageable);

        PageInfo pageInfo = new PageInfo(
                page,
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.hasNext(),
                userPage.hasPrevious()
        );

        ApiResponse<List<InternalUserResponse>> apiResponse = new ApiResponse<>(
                "success",
                "Users retrieved successfully",
                userPage.getContent(),
                pageInfo,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<List<InternalUserResponse>>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page
    ) {
        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE, Sort.by("id").ascending());
        Page<InternalUserResponse> userPage = userService.searchUsers(keyword, pageable);

        PageInfo pageInfo = new PageInfo(
                page,
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.hasNext(),
                userPage.hasPrevious()
        );

        ApiResponse<List<InternalUserResponse>> apiResponse = new ApiResponse<>(
                "success",
                "Search results retrieved successfully",
                userPage.getContent(),
                pageInfo,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<InternalUserResponse>> getOne(@PathVariable Long id) {
        InternalUserResponse user = userService.getUserById(id);
        ApiResponse<InternalUserResponse> apiResponse = new ApiResponse<>(
                "success",
                "User retrieved successfully",
                user,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<InternalUserResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid InternalUserRequest req
    ) {
        InternalUserResponse updated = userService.updateUser(id, req);
        ApiResponse<InternalUserResponse> apiResponse = new ApiResponse<>(
                "success",
                "User updated successfully",
                updated,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        ApiResponse<String> apiResponse = new ApiResponse<>(
                "success",
                "User deleted successfully",
                null,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/restore/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<String>> restore(@PathVariable Long id) {
        userService.restoreUser(id);
        ApiResponse<String> apiResponse = new ApiResponse<>(
                "success",
                "User restored successfully",
                null,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }
}
