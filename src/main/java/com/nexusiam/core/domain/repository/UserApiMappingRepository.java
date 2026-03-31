package com.nexusiam.core.domain.repository;

import com.nexusiam.core.domain.entity.UserApiMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserApiMappingRepository extends JpaRepository<UserApiMapping, Long> {

    List<UserApiMapping> findByUserIdAndIsActiveTrue(Long userId);

    List<UserApiMapping> findByUserId(Long userId);

    List<UserApiMapping> findByUserRoleAndIsActiveTrue(String userRole);

    Optional<UserApiMapping> findByUserIdAndApiSourceId(Long userId, Long apiSourceId);

    Optional<UserApiMapping> findByUserIdAndApiSourceIdAndIsActiveTrue(Long userId, Long apiSourceId);

    @Query(value = "SELECT EXISTS(" +
            "SELECT 1 FROM etp_exchange_portal.user_api_mapping uam " +
            "JOIN etp_exchange_portal.api_source apis ON uam.api_source_id = apis.id " +
            "WHERE uam.user_id = :userId " +
            "AND apis.api_path = :apiPath " +
            "AND apis.api_method = :httpMethod " +
            "AND uam.is_active = true" +
            ")", nativeQuery = true)
    boolean hasPermission(@Param("userId") Long userId,
                         @Param("apiPath") String apiPath,
                         @Param("httpMethod") String httpMethod);

    @Query("SELECT m FROM UserApiMapping m " +
           "WHERE m.userId = :userId AND m.isActive = true")
    List<UserApiMapping> findUserAccessibleApisWithDetails(@Param("userId") Long userId);

    @Query("SELECT DISTINCT m.userId FROM UserApiMapping m " +
           "WHERE m.apiSourceId = :apiSourceId AND m.isActive = true")
    List<Long> findUserIdsByApiSourceId(@Param("apiSourceId") Long apiSourceId);

    @Query(value = "SELECT DISTINCT uam.user_id FROM etp_exchange_portal.user_api_mapping uam " +
            "JOIN etp_exchange_portal.api_source apis ON uam.api_source_id = apis.id " +
            "WHERE apis.api_path = :apiPath " +
            "AND apis.api_method = :apiMethod " +
            "AND uam.is_active = true",
            nativeQuery = true)
    List<Long> findUserIdsByApiPathAndMethod(@Param("apiPath") String apiPath,
                                             @Param("apiMethod") String apiMethod);

    void deleteByUserId(Long userId);

    void deleteByApiSourceId(Long apiSourceId);

    long countByUserIdAndIsActiveTrue(Long userId);

    long countByApiSourceId(Long apiSourceId);

    boolean existsByUserIdAndApiSourceId(Long userId, Long apiSourceId);

    @Query(value = "SELECT m.* FROM user_api_mapping m " +
           "JOIN api_source a ON m.api_source_id = a.id " +
           "WHERE m.user_id = :userId " +
           "AND a.module = :module " +
           "AND m.is_active = true", nativeQuery = true)
    List<UserApiMapping> findByUserIdAndModule(@Param("userId") Long userId,
                                               @Param("module") String module);

    @Query("SELECT m.userRole as role, COUNT(m) as count " +
           "FROM UserApiMapping m " +
           "WHERE m.isActive = true " +
           "GROUP BY m.userRole")
    List<Object[]> countPermissionsByRole();

    @Query(value = "SELECT a.module as module, COUNT(m.id) as count " +
           "FROM user_api_mapping m " +
           "JOIN api_source a ON m.api_source_id = a.id " +
           "WHERE m.user_id = :userId " +
           "AND m.is_active = true " +
           "GROUP BY a.module", nativeQuery = true)
    List<Object[]> countUserPermissionsByModule(@Param("userId") Long userId);
}
