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
    name = "api_source",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_api_path_method",
            columnNames = {"api_path", "api_method"}
        )
    },
    indexes = {
        @Index(name = "idx_api_source_api_path", columnList = "api_path"),
        @Index(name = "idx_api_source_api_method", columnList = "api_method"),
        @Index(name = "idx_api_source_module", columnList = "module"),
        @Index(name = "idx_api_source_path_method", columnList = "api_path, api_method")
    }
)
public class ApiSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_path", nullable = false, length = 255)
    private String apiPath;

    @Column(name = "api_method", nullable = false, length = 10)
    private String apiMethod;

    @Column(name = "module", length = 100)
    private String module;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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
