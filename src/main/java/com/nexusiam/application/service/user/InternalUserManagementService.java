package com.nexusiam.application.service.user;

import com.nexusiam.application.dto.request.InternalUserRequest;
import com.nexusiam.application.dto.response.InternalUserResponse;
import com.nexusiam.core.domain.entity.InternalUser;
import com.nexusiam.core.domain.repository.InternalUserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class InternalUserManagementService implements InternalUserService {

    private final PasswordEncoder passwordEncoder;
    private final InternalUserRepository userRepository;

    @Autowired
    public InternalUserManagementService(InternalUserRepository userRepository,
                                        PasswordEncoder passwordEncoder) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    @Override
    @Transactional
    public InternalUserResponse createUser(InternalUserRequest request) {
        validateEmailNotExists(request.getEmail(), null);

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required for user creation.");
        }

        if (request.getRole() == null || request.getRole().isBlank()) {
            throw new IllegalArgumentException("Role is required for user creation.");
        }

        InternalUser user = new InternalUser();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setOrganisation(request.getOrganisation() != null ? request.getOrganisation() : deriveOrganisationFromRole(request.getRole()));
        user.setStatus(request.getStatus() != null ? request.getStatus() : "PENDING");
        user.setNotes(request.getNotes());
        user.setIsDeleted(false);

        if (request.getCreatedBy() != null) {
            user.setCreatedBy(request.getCreatedBy());
        }

        InternalUser saved = userRepository.save(user);
        return toUserResponse(saved);
    }

    @Override
    public Page<InternalUserResponse> getAllUsers(Pageable pageable) {

        return userRepository.findAll(pageable)
                .map(this::toUserResponse);
    }

    @Override
    public InternalUserResponse getUserById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null.");
        }

        InternalUser user = userRepository.findById(id)
                .orElse(null);

        return user != null ? toUserResponse(user) : null;
    }

    @Override
    public InternalUserResponse getUserByEmail(String email) {
        InternalUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new RuntimeException("User account is deactivated");
        }

        return toUserResponse(user);
    }

    @Override
    @Transactional
    public InternalUserResponse updateUser(Long id, InternalUserRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null.");
        }

        InternalUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        validateEmailNotExists(request.getEmail(), id);

        user.setName(request.getName());
        user.setEmail(request.getEmail());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRole() != null && !request.getRole().isBlank()) {
            user.setRole(request.getRole());
        }

        if (request.getOrganisation() != null) {
            user.setOrganisation(request.getOrganisation());
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());

            if ("DISABLED".equals(request.getStatus())) {
                user.setIsDeleted(true);
            } else if (user.getIsDeleted()) {
                user.setIsDeleted(false);
            }
        }

        if (request.getNotes() != null) {
            user.setNotes(request.getNotes());
        }

        return toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null.");
        }

        userRepository.findById(id)
                .ifPresent(u -> {
                    u.setIsDeleted(true);
                    u.setStatus("DISABLED");
                    userRepository.save(u);
                });
    }

    @Override
    @Transactional
    public void restoreUser(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null.");
        }

        userRepository.findById(id)
                .ifPresent(u -> {
                    u.setIsDeleted(false);

                    if (u.getLastLoginAt() != null) {
                        u.setStatus("ACTIVE");
                    } else {
                        u.setStatus("PENDING");
                    }
                    userRepository.save(u);
                });
    }

    @Override
    public Page<InternalUserResponse> searchUsers(String keyword, Pageable pageable) {
        return userRepository.searchByNameOrEmail(keyword, pageable)
                .map(this::toUserResponse);
    }

    private void validateEmailNotExists(String email, Long ignoreId) {
        boolean exists;
        if (ignoreId == null) {
            exists = userRepository.existsByEmail(email);
        } else {
            exists = userRepository.existsByEmailAndIdNot(email, ignoreId);
        }

        if (exists) {
            throw new IllegalArgumentException("Email already exists. Please use another email.");
        }
    }

    private String deriveOrganisationFromRole(String role) {
        return switch (role) {
            case "ADMIN" -> "ADMIN";
            case "SUPER_ADMIN" -> "SUPER_ADMIN";
            case "USER" -> "USER";
            default -> "Technical partner";
        };
    }

    private InternalUserResponse toUserResponse(InternalUser user) {
        InternalUserResponse response = new InternalUserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setOrganisation(user.getOrganisation());
        response.setStatus(user.getStatus());
        response.setNotes(user.getNotes());
        response.setIsDeleted(user.getIsDeleted());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setLastLoginDevice(user.getLastLoginDevice());
        response.setLastLoginBrowser(user.getLastLoginBrowser());
        response.setCreatedBy(user.getCreatedBy());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());

        if (user.getCreatedBy() != null) {
            userRepository.findById(Objects.requireNonNull(user.getCreatedBy()))
                .ifPresent(creator -> {
                    response.setCreatedByName(creator.getName());
                    response.setCreatedByEmail(creator.getEmail());
                });
        }

        return response;
    }
}
