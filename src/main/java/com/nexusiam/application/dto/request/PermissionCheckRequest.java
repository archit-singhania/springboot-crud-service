package com.nexusiam.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckRequest {

    @NotNull(message = "User ID cannot be null")
    private Long userId;

    @NotBlank(message = "API path cannot be blank")
    private String apiPath;

    @NotBlank(message = "HTTP method cannot be blank")
    private String httpMethod;
}
