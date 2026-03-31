package com.nexusiam.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleSwitchRequest {

    @NotBlank(message = "New role is required")
    @Pattern(regexp = "BUYER|SELLER", message = "Role must be either BUYER or SELLER")
    @JsonProperty("new_role")
    private String newRole;
}
