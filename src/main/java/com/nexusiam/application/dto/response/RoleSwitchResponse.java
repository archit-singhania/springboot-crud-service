package com.nexusiam.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleSwitchResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("id_token")
    private String idToken;

    @JsonProperty("current_role")
    private String currentRole;

    @JsonProperty("available_roles")
    private List<String> availableRoles;

    @JsonProperty("profile_id")
    private String profileId;

    @JsonProperty("grp_id")
    private String grpId;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in_seconds")
    private Long expiresInSeconds;

    @JsonProperty("message")
    private String message;
}
