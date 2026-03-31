package com.nexusiam.application.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkApiPermissionRequest {

    @NotNull(message = "User ID cannot be null")
    private Long userId;

    private String userRole;

    @NotEmpty(message = "At least one permission must be specified")
    private List<PermissionEntry> permissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionEntry {

        @NotNull(message = "API path cannot be null")
        private String apiPath;

        @NotEmpty(message = "At least one method must be specified")
        private List<String> allowedMethods;

        private String module;

        private String description;

        @Builder.Default
        private Boolean isActive = true;
    }
}
