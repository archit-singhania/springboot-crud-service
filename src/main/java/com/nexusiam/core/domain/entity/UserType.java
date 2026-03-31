package com.nexusiam.core.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(
    name = "user_types",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "idx_user_types_type_role",
            columnNames = {"type", "role"}
        )
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "\"desc\"", columnDefinition = "text")
    private String description;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    @PrePersist
    protected void onCreate() {
        if (createdDate == null) {
            createdDate = Instant.now();
        }
        if (updatedDate == null) {
            updatedDate = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = Instant.now();
    }
}
