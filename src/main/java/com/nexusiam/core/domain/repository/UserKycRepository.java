package com.nexusiam.core.domain.repository;

import com.nexusiam.core.domain.entity.UserKyc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserKycRepository extends JpaRepository<UserKyc, Long> {

    Optional<UserKyc> findByGrpIdAndIsActiveTrue(String grpId);

    Optional<UserKyc> findFirstByGrpIdOrderByCreatedAtDesc(String grpId);

    Optional<UserKyc> findByGrpIdAndStatusAndIsActiveTrue(String grpId, Short status);

    boolean existsByGrpId(String grpId);

    long countByGrpIdAndIsActiveTrue(String grpId);
}
