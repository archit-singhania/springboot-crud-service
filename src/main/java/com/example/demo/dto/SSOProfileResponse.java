package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SSOProfileResponse {

    // Basic Identity
    @JsonProperty("profile_id")
    private String profileId;

    @JsonProperty("org_id")
    private String orgId;

    @JsonProperty("user_category")
    private String userCategory;

    // Company Details
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

    // Authorized Person Details
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

    // Status Fields
    @JsonProperty("status")
    private String status;

    @JsonProperty("compliance_status")
    private String complianceStatus;

    @JsonProperty("exchange_access")
    private String exchangeAccess;

    @JsonProperty("valid_till")
    private Instant validTill;

    // Portal Registrations (array of registration objects)
    @JsonProperty("registrations")
    private List<Map<String, Object>> registrations;

    // Raw metadata (if SSO sends additional fields)
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    // For backward compatibility with Okta's userinfo response
    @JsonProperty("sub")
    private String sub; // Okta returns this instead of profile_id

    @JsonProperty("preferred_username")
    private String preferredUsername;

    @JsonProperty("name")
    private String name;

    // Auto-map 'sub' to 'profile_id' if profile_id is null
    public String getProfileId() {
        return profileId != null ? profileId : sub;
    }

    // Auto-map email variants
    public String getEmail() {
        if (email != null && !email.isEmpty()) return email;
        if (preferredUsername != null && preferredUsername.contains("@")) return preferredUsername;
        return null;
    }
}