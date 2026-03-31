package com.nexusiam.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccessibleApisResponse {

    private Long userId;

    private String userName;

    private String userEmail;

    private String userRole;

    private Integer totalApis;

    private List<ApiAccessInfo> accessibleApis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiAccessInfo {

        private String apiPath;

        private List<String> allowedMethods;

        private String module;

        private String description;

        private Boolean isActive;
    }
}
