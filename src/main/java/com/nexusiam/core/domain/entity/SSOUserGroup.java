package com.nexusiam.core.domain.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexusiam.application.dto.RegistrationDTO;
import com.nexusiam.application.service.mapper.UserGroupDataMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "sso_user_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SSOUserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, unique = true)
    private String profileId;

    @Column(name = "grp_id", nullable = false)
    private String grpId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "cin_number")
    private String cinNumber;

    @Column(name = "group_pan")
    private String groupPan;

    @Column(name = "authorized_person_name")
    private String authorizedPersonName;

    @Column(name = "designation")
    private String designation;

    @Column(name = "email")
    private String email;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "landline")
    private String landline;

    @Builder.Default
    @Column(name = "status", nullable = false)
    private String status = "Active";

    @Column(name = "user_type_id", nullable = false)
    private Short userTypeId;

    @Column(name = "compliance_status")
    private String complianceStatus;

    @Column(name = "exchange_access")
    private String exchangeAccess;

    @Column(name = "valid_till")
    private Instant validTill;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registrations", columnDefinition = "jsonb", nullable = false)
    private JsonNode registrations;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdDate == null) {
            createdDate = now;
        }
        if (updatedDate == null) {
            updatedDate = now;
        }
        if (status == null) {
            status = "Active";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = Instant.now();
    }

    public Map<String, Object> toProfileMap(UserGroupDataMapper mapper, String profileId) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("profile_id", this.profileId != null ? this.profileId : profileId);
        profile.put("grp_id", this.grpId);
        profile.put("email", this.email);
        profile.put("authorized_person_name", this.authorizedPersonName);
        profile.put("designation", this.designation);
        profile.put("mobile", this.mobile);
        profile.put("landline", this.landline);
        profile.put("company_name", this.companyName);
        profile.put("cin_number", this.cinNumber);
        profile.put("pan_number", this.groupPan);
        profile.put("status", this.status);
        profile.put("compliance_status", this.complianceStatus);
        profile.put("exchange_access", this.exchangeAccess);
        profile.put("valid_till", this.validTill);

        if (this.registrations != null && mapper != null) {
            try {
                com.nexusiam.application.service.user.RegistrationService registrationService =
                    new com.nexusiam.application.service.user.RegistrationService(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                    );
                List<RegistrationDTO> registrations = registrationService.parseRegistrations(this.registrations);
                List<Map<String, Object>> regMaps = registrations.stream()
                        .map(mapper::convertRegistrationDTOToMap)
                        .toList();
                profile.put("registrations", regMaps);
            } catch (Exception e) {
                profile.put("registrations", List.of());
            }
        } else {
            profile.put("registrations", List.of());
        }

        return profile;
    }
}
