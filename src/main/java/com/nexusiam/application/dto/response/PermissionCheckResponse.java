package com.nexusiam.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckResponse {

    private Boolean hasPermission;

    private Long userId;

    private String apiPath;

    private String httpMethod;

    private String message;

    public static PermissionCheckResponse allowed(Long userId, String apiPath, String httpMethod) {
        return PermissionCheckResponse.builder()
                .hasPermission(true)
                .userId(userId)
                .apiPath(apiPath)
                .httpMethod(httpMethod)
                .message("Permission granted")
                .build();
    }

    public static PermissionCheckResponse denied(Long userId, String apiPath, String httpMethod, String reason) {
        return PermissionCheckResponse.builder()
                .hasPermission(false)
                .userId(userId)
                .apiPath(apiPath)
                .httpMethod(httpMethod)
                .message(reason)
                .build();
    }
}
