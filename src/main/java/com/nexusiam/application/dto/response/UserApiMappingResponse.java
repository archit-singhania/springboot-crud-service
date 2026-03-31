package com.nexusiam.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserApiMappingResponse {

    private Long id;

    private Long userId;

    private String userRole;

    private String userName;

    private String userEmail;

    private Long apiSourceId;

    private String apiPath;

    private String apiMethod;

    private String module;

    private String description;

    private Boolean isActive;

    private Instant createdDate;

    private Instant updatedDate;
}
