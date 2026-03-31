package com.nexusiam.core.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "user_kyc")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grp_id", nullable = false)
    private String grpId;

    @Column(name = "user_type_id")
    private Short userTypeId;

    @Column(name = "act_holder_name", nullable = false)
    private String actHolderName;

    @Column(name = "act_number", nullable = false)
    private String actNumber;

    @Column(name = "act_number_masked")
    private String actNumberMasked;

    @Column(name = "bank_ifsc", nullable = false, length = 11)
    private String bankIfsc;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private Short status = 0;

    @Column(name = "verified_at")
    private ZonedDateTime verifiedAt;

    @Column(name = "last_updated_at")
    private ZonedDateTime lastUpdatedAt;

    @Column(name = "quarter_lock_date")
    private ZonedDateTime quarterLockDate;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = 0;
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
