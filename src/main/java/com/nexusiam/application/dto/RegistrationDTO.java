package com.nexusiam.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationDTO {

    @JsonProperty("portal_id")
    private String portalId;

    @JsonProperty("portal_name")
    private String portalName;

    @JsonProperty("unit_id")
    private String unitId;

    @JsonProperty("unit_address")
    private String unitAddress;

    @JsonProperty("state")
    private String state;

    @JsonProperty("pincode")
    private String pincode;

    @JsonProperty("gst_number")
    private String gstNumber;

    @JsonProperty("authorized_person_name")
    private String authorizedPersonName;

    @JsonProperty("designation")
    private String designation;

    @JsonProperty("user_pan_number")
    private String userPanNumber;

    @JsonProperty("email")
    private String email;

    @JsonProperty("mobile")
    private String mobile;

    @JsonProperty("role")
    private String role;

    @JsonProperty("type_id")
    private String typeId;

    @JsonProperty("registration_number")
    private String registrationNumber;

    @JsonProperty("status")
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
