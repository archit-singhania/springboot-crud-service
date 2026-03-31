package com.nexusiam.core.domain.repository;

import org.springframework.lang.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.nexusiam.core.domain.entity.UserType;

import java.util.Optional;

@Repository
public interface UserTypeRepository extends JpaRepository<UserType, Short> {
    Optional<UserType> findByType(String type);
    Optional<UserType> findByRole(String role);

    Optional<UserType> findFirstByType(String type);
    Optional<UserType> findFirstByRole(String role);

    boolean existsByType(String type);
    boolean existsByRole(String role);
    boolean existsById(@NonNull Short id);
}
