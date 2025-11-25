package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SSOProfileResponse {
    @JsonProperty("org_id")
    private String orgId;
    
    @JsonProperty("profile_id")
    private String profileId;
    
    @JsonProperty("company_name")
    private String companyName;
    
    @JsonProperty("company_address")
    private String companyAddress;
    
    private String state;
    private String pincode;
    
    @JsonProperty("gst_number")
    private String gstNumber;
    
    @JsonProperty("cin_number")
    private String cinNumber;
    
    @JsonProperty("pan_number")
    private String panNumber;
    
    @JsonProperty("authorized_person_name")
    private String authorizedPersonName;
    
    private String designation;
    private String email;
    private String mobile;
    private String landline;
    
    private String status;
    
    @JsonProperty("compliance_status")
    private String complianceStatus;
    
    @JsonProperty("exchange_access")
    private String exchangeAccess;
    
    @JsonProperty("valid_till")
    private Instant validTill;
    
    @JsonProperty("last_login_on")
    private Instant lastLoginOn;
    
    private List<Registration> registrations;
    
    @JsonProperty("issued_at")
    private Instant issuedAt;
    
    private String signature;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Registration {
        @JsonProperty("org_id")
        private String orgId;

        @JsonProperty("portal_id")
        private String portalId;
        
        @JsonProperty("portal_name")
        private String portalName;
        
        @JsonProperty("party_id")
        private String partyId;
        
        @JsonProperty("unit_address")
        private String unitAddress;
        
        private String state;
        private String pincode;
        
        @JsonProperty("gst_number")
        private String gstNumber;
        
        private String role;
        
        @JsonProperty("type_id")
        private String typeId;
        
        @JsonProperty("registration_number")
        private String registrationNumber;
        
        private String status;
        
        @JsonProperty("valid_till")
        private Instant validTill;
        
        @JsonProperty("portal_internal_id")
        private String portalInternalId;
        
        @JsonProperty("authorized_categories")
        private List<String> authorizedCategories;
        
        @JsonProperty("certificate_count")
        private Integer certificateCount;
    }
}