package com.nexusiam.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InternalUserResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String organisation;
    private String status;
    private String notes;
    private Boolean isDeleted;
    private Instant lastLoginAt;
    private String lastLoginDevice;
    private String lastLoginBrowser;
    private Long createdBy;
    private String createdByName;
    private String createdByEmail;
    private Instant createdAt;
    private Instant updatedAt;
}
