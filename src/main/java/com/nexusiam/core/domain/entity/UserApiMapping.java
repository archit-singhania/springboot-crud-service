package com.nexusiam.core.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "user_api_mapping",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_user_api_source",
            columnNames = {"user_id", "api_source_id"}
        )
    },
    indexes = {
        @Index(name = "idx_user_api_mapping_user", columnList = "user_id"),
        @Index(name = "idx_user_api_mapping_user_role", columnList = "user_role"),
        @Index(name = "idx_user_api_mapping_api_source", columnList = "api_source_id"),
        @Index(name = "idx_user_api_mapping_is_active", columnList = "is_active"),
        @Index(name = "idx_user_api_mapping_user_active", columnList = "user_id, is_active")
    }
)
public class UserApiMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Column(name = "api_source_id", nullable = false)
    private Long apiSourceId;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    @PrePersist
    protected void onCreate() {
        this.createdDate = Instant.now();
        this.updatedDate = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedDate = Instant.now();
    }
}
