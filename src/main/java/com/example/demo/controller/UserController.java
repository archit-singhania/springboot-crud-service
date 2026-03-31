package com.example.demo.controller;

import com.example.demo.exception.NotFoundException;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.PageInfo;
import com.example.demo.dto.UserRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.service.UserService;
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
@RequestMapping("/users")
@Validated
public class UserController {

    private final UserService userService;

    private static final int DEFAULT_PAGE_SIZE = 5;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserRequest req) {
        UserResponse response = userService.createUser(req);
        ApiResponse<UserResponse> apiResponse = new ApiResponse<>(
                "success",
                "User created successfully",
                response,
                HttpStatus.CREATED.value()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAll(
            @RequestParam(defaultValue = "1") int page
    ) {
        Pageable pageable = PageRequest.of(page - 1, DEFAULT_PAGE_SIZE, Sort.by("id").ascending());
        Page<UserResponse> userPage = userService.getAllUsers(pageable);

        PageInfo pageInfo = new PageInfo(
                page,
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.hasNext(),
                userPage.hasPrevious()
        );

        ApiResponse<List<UserResponse>> apiResponse = new ApiResponse<>(
                "success",
                "Users retrieved successfully",
                userPage.getContent(),
                pageInfo,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page
    ) {
        Pageable pageable = PageRequest.of(page - 1, DEFAULT_PAGE_SIZE, Sort.by("id").ascending());
        Page<UserResponse> userPage = userService.searchUsers(keyword, pageable);

        PageInfo pageInfo = new PageInfo(
                page,
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.hasNext(),
                userPage.hasPrevious()
        );

        ApiResponse<List<UserResponse>> apiResponse = new ApiResponse<>(
                "success",
                "Search results retrieved successfully",
                userPage.getContent(),
                pageInfo,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'USER')")
    public ResponseEntity<ApiResponse<UserResponse>> getOne(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        ApiResponse<UserResponse> apiResponse = new ApiResponse<>(
                "success",
                "User retrieved successfully",
                user,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid UserRequest req
    ) {
        UserResponse updated = userService.updateUser(id, req);
        ApiResponse<UserResponse> apiResponse = new ApiResponse<>(
                "success",
                "User updated successfully",
                updated,
                HttpStatus.OK.value()
        );
        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            ApiResponse<String> apiResponse = new ApiResponse<>(
                    "success",
                    "User deleted successfully",
                    null,
                    HttpStatus.OK.value()
            );
            return ResponseEntity.ok(apiResponse);
        } catch (NotFoundException ex) {
            ApiResponse<String> apiResponse = new ApiResponse<>(
                    "error",
                    "User not found or already deleted",
                    null,
                    HttpStatus.NOT_FOUND.value()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiResponse);
        }
    }

    @PutMapping("/restore/{id}")
    @PreAuthorize("hasRole('ADMIN')")
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
