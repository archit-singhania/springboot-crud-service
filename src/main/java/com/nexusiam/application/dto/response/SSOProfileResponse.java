package com.nexusiam.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SSOProfileResponse {
    @JsonProperty("profile_id")
    private String profileId;

    @JsonProperty("grp_id")
    private String grpId;

    @JsonProperty("user_category")
    private String userCategory;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("company_address")
    private String companyAddress;

    @JsonProperty("state")
    private String state;

    @JsonProperty("pincode")
    private String pincode;

    @JsonProperty("gst_number")
    private String gstNumber;

    @JsonProperty("cin_number")
    private String cinNumber;

    @JsonProperty("pan_number")
    private String panNumber;

    @JsonProperty("authorized_person_name")
    private String authorizedPersonName;

    @JsonProperty("designation")
    private String designation;

    @JsonProperty("email")
    private String email;

    @JsonProperty("mobile")
    private String mobile;

    @JsonProperty("landline")
    private String landline;

    @JsonProperty("status")
    private String status;

    @JsonProperty("compliance_status")
    private String complianceStatus;

    @JsonProperty("exchange_access")
    private String exchangeAccess;

    @JsonProperty("valid_till")
    private Instant validTill;

    @JsonProperty("registrations")
    private List<Map<String, Object>> registrations;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("sub")
    private String sub;

    @JsonProperty("preferred_username")
    private String preferredUsername;

    @JsonProperty("name")
    private String name;

    public String getProfileId() {
        return profileId != null ? profileId : sub;
    }

     public String getEmail() {
        if (email != null && !email.isEmpty()) return email;
        if (preferredUsername != null && preferredUsername.contains("@")) {
            log.warn("email not found, using preferred_username: {}", preferredUsername);
            return preferredUsername;
        }
        String fallbackEmail = getProfileId() + "@exchange.local";
        log.warn("No valid email found, using fallback: {}", fallbackEmail);
        return fallbackEmail;
    }

    public String getGrpId() {
        if (grpId != null && !grpId.isEmpty()) {
            return grpId;
        }
        if (sub != null && !sub.isEmpty()) {
            log.warn("grp_id not found in response, using sub as fallback: {}", sub);
            return sub;
        }
        String pid = getProfileId();
        log.warn("Neither grp_id nor sub found, using profileId as grpId: {}", pid);
        return pid;
    }
}
