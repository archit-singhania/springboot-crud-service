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
public class BulkUserApiMappingRequest {

    @NotNull(message = "User ID cannot be null")
    private Long userId;

    private String userRole;

    @NotEmpty(message = "At least one API source ID must be specified")
    private List<Long> apiSourceIds;

    @Builder.Default
    private Boolean isActive = true;
}
