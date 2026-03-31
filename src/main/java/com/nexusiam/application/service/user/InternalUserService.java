package com.nexusiam.application.service.user;

import com.nexusiam.application.dto.request.InternalUserRequest;
import com.nexusiam.application.dto.response.InternalUserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InternalUserService {
    InternalUserResponse getUserByEmail(String email);
    InternalUserResponse createUser(InternalUserRequest request);
    Page<InternalUserResponse> getAllUsers(Pageable pageable);
    InternalUserResponse getUserById(Long id);
    InternalUserResponse updateUser(Long id, InternalUserRequest request);
    void deleteUser(Long id);
    void restoreUser(Long id);
    Page<InternalUserResponse> searchUsers(String keyword, Pageable pageable);
}
