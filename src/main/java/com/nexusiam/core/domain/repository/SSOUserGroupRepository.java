package com.nexusiam.core.domain.repository;

import com.nexusiam.core.domain.entity.SSOUserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SSOUserGroupRepository extends JpaRepository<SSOUserGroup, Long> {
    Optional<SSOUserGroup> findByProfileId(String profileId);

    boolean existsByProfileId(String profileId);

    Optional<SSOUserGroup> findByGrpId(String grpId);

    Optional<SSOUserGroup> findByEmail(String email);

    List<SSOUserGroup> findByStatus(String status);

    List<SSOUserGroup> findByExchangeAccess(String exchangeAccess);

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM SSOUserGroup g " +
           "WHERE g.grpId = :grpId " +
           "AND g.status = 'Active' " +
           "AND g.exchangeAccess = 'allowed' " +
           "AND g.complianceStatus = 'active' " +
           "AND (g.validTill IS NULL OR g.validTill >= :currentDate)")
    boolean isGroupActiveForTrading(@Param("grpId") String grpId,
                                    @Param("currentDate") Instant currentDate);

    @Query("SELECT g FROM SSOUserGroup g " +
           "WHERE g.status = 'Active' " +
           "AND g.exchangeAccess = 'allowed'")
    List<SSOUserGroup> findAllActiveGroups();

    @Query("SELECT g FROM SSOUserGroup g " +
           "WHERE g.lastSyncedAt IS NULL " +
           "OR g.lastSyncedAt < :thresholdTime")
    List<SSOUserGroup> findGroupsNeedingSync(@Param("thresholdTime") Instant thresholdTime);

    @Query("SELECT COUNT(g) FROM SSOUserGroup g WHERE g.status = 'Active'")
    long countActiveGroups();

    @Modifying
    @Query(value = """
        INSERT INTO sso_user_groups (
            profile_id, grp_id, company_name, cin_number, group_pan,
            authorized_person_name, designation, email, mobile, landline,
            status, user_type_id, compliance_status, exchange_access,
            valid_till, last_synced_at, registrations, created_date, updated_date
        ) VALUES (
            :#{#userGroup.profileId},
            :#{#userGroup.grpId},
            :#{#userGroup.companyName},
            :#{#userGroup.cinNumber},
            :#{#userGroup.groupPan},
            :#{#userGroup.authorizedPersonName},
            :#{#userGroup.designation},
            :#{#userGroup.email},
            :#{#userGroup.mobile},
            :#{#userGroup.landline},
            :#{#userGroup.status},
            :#{#userGroup.userTypeId},
            :#{#userGroup.complianceStatus},
            :#{#userGroup.exchangeAccess},
            :#{#userGroup.validTill},
            :#{#userGroup.lastSyncedAt},
            CAST(:#{#userGroup.registrations} AS jsonb),
            COALESCE(:#{#userGroup.createdDate}, CURRENT_TIMESTAMP),
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (profile_id) DO UPDATE SET
            grp_id = EXCLUDED.grp_id,
            company_name = EXCLUDED.company_name,
            cin_number = EXCLUDED.cin_number,
            group_pan = EXCLUDED.group_pan,
            authorized_person_name = EXCLUDED.authorized_person_name,
            designation = EXCLUDED.designation,
            email = EXCLUDED.email,
            mobile = EXCLUDED.mobile,
            landline = EXCLUDED.landline,
            status = EXCLUDED.status,
            user_type_id = EXCLUDED.user_type_id,
            compliance_status = EXCLUDED.compliance_status,
            exchange_access = EXCLUDED.exchange_access,
            valid_till = EXCLUDED.valid_till,
            last_synced_at = EXCLUDED.last_synced_at,
            registrations = EXCLUDED.registrations,
            updated_date = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsertUserGroup(@Param("userGroup") SSOUserGroup userGroup);
}
