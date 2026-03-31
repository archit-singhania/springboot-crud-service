package com.nexusiam.core.domain.repository;

import com.nexusiam.core.domain.entity.InternalUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InternalUserRepository extends JpaRepository<InternalUser, Long> {
    List<InternalUser> findByIsDeletedFalse();

    Page<InternalUser> findByIsDeletedFalse(Pageable pageable);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByEmailAndIsDeletedFalse(String email);

    boolean existsByEmailAndIdNotAndIsDeletedFalse(String email, Long id);

    @Query("SELECT u FROM InternalUser u WHERE " +
            "(LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<InternalUser> searchByNameOrEmail(@Param("keyword") String keyword, Pageable pageable);

    Optional<InternalUser> findByEmail(String email);

    Optional<InternalUser> findByEmailAndIsDeletedFalse(String email);

    List<InternalUser> findByRoleAndIsDeletedFalse(String role);

    Page<InternalUser> findByOrganisation(String organisation, Pageable pageable);

    Page<InternalUser> findByStatus(String status, Pageable pageable);

    Page<InternalUser> findByOrganisationAndStatus(String organisation, String status, Pageable pageable);
}
