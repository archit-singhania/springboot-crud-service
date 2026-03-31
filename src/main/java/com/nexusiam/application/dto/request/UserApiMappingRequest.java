package com.nexusiam.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserApiMappingRequest {

    @NotNull(message = "User ID cannot be null")
    private Long userId;

    private String userRole;

    @NotNull(message = "API source ID cannot be null")
    private Long apiSourceId;

    @Builder.Default
    private Boolean isActive = true;
}
