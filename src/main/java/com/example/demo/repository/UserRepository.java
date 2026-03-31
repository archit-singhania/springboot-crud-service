package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByIsDeletedFalse();
    Page<User> findByIsDeletedFalse(Pageable pageable);
    boolean existsByEmailAndIsDeletedFalse(String email);
    boolean existsByEmailAndIdNotAndIsDeletedFalse(String email, Long id);
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
            "(LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchByNameOrEmail(@Param("keyword") String keyword, Pageable pageable);
    Optional<User> findByEmail(String email);
}
